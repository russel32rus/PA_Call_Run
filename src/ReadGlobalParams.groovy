/*******************************************************************************************************
 * Класс-компонент ReadGlobalParams.
 * Используется в овновном батч-процессе для чтения глобальных параметров СПР
 * из внутренней БД (таблицы SM_CONF, SM_CONF_BATCH_SQL, SM_CONF_BATCH_MAP)
 * в память JVM (объекты globalConf, globalConfSql, globalConfMap)
 *******************************************************************************************************/


import com.experian.eda.decisionagent.interfaces.os390.BatchJSEMObjectInterface
import com.experian.eda.framework.runtime.dynamic.HierarchicalDatasource
import com.experian.eda.framework.runtime.dynamic.IHData
import com.zaxxer.hikari.HikariDataSource
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement
import java.time.Instant

import static CustomUtils.*
import static DatabaseHelpers.*
import static SmProcessingHelpers.*
import static DecisionAgentTask.*
import static ExtBatchSaveResultData.*

@CompileStatic
class ReadGlobalParams {

    private static final Logger log = LoggerFactory.getLogger(this)

    static void readGlobalParams() {
        Instant start = Instant.now()
        Statement st
        ResultSet rs
        Connection pcsmConn = getPcsmDbConnection()
        try {
            /*****************************************************************************
             *  Чтение всех записей таблицы SM_CONF_BATCH_SQL в память.
             *  Грузим первой, чтобы получить SQL-запрос логгера в БД (dbLoggerStatement)
             *****************************************************************************/
            log.debug("Loading $SM_CONF_BATCH_SQL_TABLE_NAME table...")
            st = pcsmConn.createStatement()
            log.info("Check 1")
            log.info(SM_CONF_BATCH_SQL_SELECT_QUERY)
            rs = st.executeQuery(SM_CONF_BATCH_SQL_SELECT_QUERY)
            log.info("Check 2")
            ArrayList<Integer> allBindBitsOffsets = new ArrayList<>()
            while (rs.next()) {
                String entityName = rs.getString("ENTITY_NAME")
                String parentEntityName = rs.getString("PARENT_ENTITY_NAME")
                Integer bindBitOffset = rs.getObject("BIND_BIT_OFFSET") as Integer
                String arrayType = rs.getString("ARRAY_TYPE")
                String syncResultsetCol = rs.getString("SYNC_RESULTSET_COL")
                String mapDirection = rs.getString("MAP_DIRECTION")
                String targetDb = rs.getString("TARGET_DB")
                String smLayout = rs.getString("SM_LAYOUT")
                String clearOnCalls = rs.getString("CLEAR_ON_CALLS")

                String sqlText = rs.getString("SQL_TEXT")

                setGlobalConfSqlEntity(parentEntityName, entityName, bindBitOffset?.toInteger(), arrayType, syncResultsetCol, mapDirection, targetDb, smLayout, clearOnCalls, sqlText)

                if (bindBitOffset != null) {
                    if (allBindBitsOffsets.contains(bindBitOffset))
                        throw new Exception("SM_CONF_BATCH_SQL validation error: non-unique BIND_BIT_OFFSET value '$bindBitOffset' for entity '$entityName'")
                    else
                        allBindBitsOffsets.add(bindBitOffset)
                }
                if (bindBitOffset == null && parentEntityName == SQL_ENTITY_CUSTOMER)
                    throw new Exception("SM_CONF_BATCH_SQL validation error: null BIND_BIT_OFFSET value for entity '$entityName'")
            }
            rs.close()
            st.close()
            log.debug("globalConfSqlTreeInput = ${globalConfSqlTreeInput}")
            log.debug("globalConfSqlTreeOutput = ${globalConfSqlTreeOutput}")
            if (globalConfSqlTreeInput.size() < 1 || globalConfSqlTreeOutput.size() < 1) throw new Exception("No data found in SM_CONF_BATCH_SQL table")
            log.info("Starting server")
            log.info("$SM_CONF_BATCH_SQL_TABLE_NAME configuration table loaded successfully")
            /*****************************************************************************
             *  Валидация структуры SM_CONF_BATCH_SQL
             *****************************************************************************/
            log.info("Validating SM_CONF_BATCH_SQL entries...")
            /*try {
                clearableEntities?.each { stageCd, entityList ->
                    log.info("Checking DELETE statements for call '$stageCd'...")
                    entityList?.each { entityName ->
                        log.info("Found clearable entity '$entityName' for stage '$stageCd'. Searching associated DML-entity with 'DELETE_' prefix...")
                        try {
                            RccEntity deleteDmlEntity = getGlobalConfSqlEntity(SQL_TREE_ROOT, "DELETE_${entityName}")
                            if (deleteDmlEntity != null)
                                log.info("Check of $entityName passed - found associated DML-entity ${deleteDmlEntity.entityName}")
                            else throw new Exception("DML entity is null")
                        } catch (Exception e) {
                            throw new Exception("Not found associated DML-entity with 'DELETE_' prefix for entity $entityName (having CLEAR_ON_CALLS set) in SM_CONF_BATCH_SQL table: $e")
                        }
                    }
                }
            } catch (Exception e) {
                throw new SmValidatePcsmDbException("PSCM database validation failed: $e")
            }*/
            /*****************************************************************************
             *  Чтение всех полей таблицы SM_CONF в память
             *****************************************************************************/
            start = Instant.now()
            log.debug("Loading $SM_CONF_TABLE_NAME table...")
            st = pcsmConn.createStatement()
            rs = st.executeQuery(SM_CONF_SELECT_QUERY)
            ResultSetMetaData metadata = rs.getMetaData()
            if (rs.next()) {
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String paramName = metadata.getColumnName(i)
                    Object paramVal = rs.getObject(i)
                    setGlobalParam(paramName, paramVal)
                    if (paramName.toUpperCase().contains("STRATEGY_ALIAS")) {
                        Instant startStrategyLoad = Instant.now()
                        /********************************************************************************
                         *  Инициализация стратегий - если в имени параметра есть строка "STRATEGY_ALIAS"
                         ********************************************************************************/
                        //loadStrategy(paramVal as String)
                        //loadStrategy(RESTRUCT_STRATEGY_ALIAS) //todo убрали так как не используется
                    }
                }
                log.debug("globalConf = ${globalConf}")
            } else {
                throw new Exception("Fatal error: no data found in table $SM_CONF_TABLE_NAME")
            }

            rs.close()
            st.close()
            //logEventInfo("$SM_CONF_TABLE_NAME configuration table loaded successfully", getDurationMs(start), EVENT_INIT_CONF, log)
            log.info("$SM_CONF_TABLE_NAME configuration table loaded successfully")

            /*****************************************************************************
             *  Чтение всех записей таблицы SM_CONF_BATCH_MAP в память
             *****************************************************************************/
            start = Instant.now()
            int i = 0
            log.debug("Loading $SM_CONF_BATCH_MAP_TABLE_NAME table...")
            st = pcsmConn.createStatement()
            rs = st.executeQuery(SM_CONF_BATCH_MAP_SELECT_QUERY)
            String sqlEntityGroup = null
            String mapDirectionGroup = null
            ArrayList<HashMap<String, String>> list = null
            while (rs.next()) {
                log.debug("Processing $SM_CONF_BATCH_MAP_TABLE_NAME table record #${++i}")
                String sqlEntityName = rs.getString("ENTITY_NAME")
                String mapDirection = rs.getString("MAP_DIRECTION")
                if (sqlEntityName != sqlEntityGroup || mapDirectionGroup != mapDirection) {
                    // Новая группа или направление маппинга
                    if (list != null) {
                        (mapDirectionGroup == INPUT_MAP_DIRECTION ?
                                globalConfMapInput.put(sqlEntityGroup, list) :
                                globalConfMapOutput.put(sqlEntityGroup, list))
                        log.info("Added $mapDirection SM map group for entity $sqlEntityGroup: $list")
                    }
                    list = new ArrayList<HashMap<String, String>>()
                    sqlEntityGroup = sqlEntityName
                    mapDirectionGroup = mapDirection
                }
                HashMap<String, String> mapEntry = new HashMap<String, String>()
                String dbField = rs.getString("DB_FIELD").trim()
                String smField = rs.getString("SM_FIELD").trim()
                String fieldType = rs.getString("FIELD_TYPE").trim()
                String fieldDataType = rs.getString("DATA_TYPE")
                smField = smField.replace('%', dbField)
                mapEntry.put("DB_FIELD", dbField)
                mapEntry.put("SM_FIELD", smField)
                mapEntry.put("FIELD_TYPE", fieldType)
                mapEntry.put("FIELD_DATA_TYPE", fieldDataType)
                list.add(mapEntry)
                log.info("Found $mapDirection SM map entry: $mapEntry")
            }
            if (list != null)
                (mapDirectionGroup == INPUT_MAP_DIRECTION ?
                        globalConfMapInput.put(sqlEntityGroup, list) :
                        globalConfMapOutput.put(sqlEntityGroup, list))
            rs.close()
            log.debug("globalConfMapInout = ${globalConfMapInput}")
            log.debug("globalConfMapOutput = ${globalConfMapOutput}")
            if (globalConfMapInput.size() < 1) throw new Exception("No input mappings found in SM_CONF_BATCH_MAP table")
            if (globalConfMapOutput.size() < 1) throw new Exception("No output mappings found in SM_CONF_BATCH_MAP table")
            //logEventInfoImmediate("$SM_CONF_BATCH_MAP_TABLE_NAME configuration table loaded successfully", getDurationMs(start), EVENT_INIT_CONF, log)
            log.info("$SM_CONF_BATCH_MAP_TABLE_NAME configuration table loaded successfully")

            //TODO убрано так как не нужно на данном этапе
            //String smAlias = getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_ALIAS, true) //smAlias = PA_Call (из SM_CONF)
            //executeInitCall(smAlias)
            //executeInitCall(RESTRUCT_STRATEGY_ALIAS)
        }
        catch (SmCallException e) {
            logFatalErrorAndShutdown(e.getMessage(), EVENT_INIT_STRATEGY, ERR_INIT_STRATEGY, log, e)
        } catch (SmInitException e) {
            logFatalErrorAndShutdown(e.getMessage(), EVENT_INIT_STRATEGY, ERR_INIT_STRATEGY, log, e)
        } catch (Exception e) {
            logFatalErrorAndShutdown(e.getMessage(), EVENT_INIT_CONF, ERR_INIT_CONF, log, e)
        }
        finally {
            rs?.close()
            st?.close()
           //((HikariDataSource)pcsmDataSource).evictConnection(pcsmConn)
            pcsmConn.close()
        }

    }

    static executeInitCall(String smAlias) {
        log.info("Executing DA call [INIT_CALL] of $smAlias...")

        String smSignature = getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_SIGNATURE, true)
        int traceFlags = getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS, true) as Integer

        IHData smControlData = createSmControlBlock(smAlias, smSignature)
        IHData smCustomerData = new HierarchicalDatasource(SM_LAYOUT_CUSTOMER) as IHData
        IHData smDecisionData = new HierarchicalDatasource(SM_LAYOUT_DECISION) as IHData
        IHData smStateData = new HierarchicalDatasource(SM_LAYOUT_STATE) as IHData
        IHData[] executionData = [smControlData, smCustomerData, smDecisionData, smStateData]

        try {
            int daReturnCode = BatchJSEMObjectInterface.instance().execute(executionData, traceFlags)
            checkDaErrors(executionData, daReturnCode)
            log.info("DA call of $smAlias executed")
            Long smAliasPackGroupByFromInitCall = calcPackGroupBy(smStateData)
            packGroupByFromInitCall.put(smAlias, smAliasPackGroupByFromInitCall)
            if (smAliasPackGroupByFromInitCall == 1) throw new SmInitException("Fatal error: INIT_CALL of $smAlias returned empty list of entities")
        } catch (Exception e) {
            throw new SmCallException("Strategy call INIT_CALL of $smAlias failed. ${e.getMessage()}")
        }
        log.info("SM call INIT_CALL of $smAlias executed successfully")
    }
}

