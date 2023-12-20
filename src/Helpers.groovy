
import com.experian.eda.component.decisionagent.*
import com.experian.eda.decisionagent.interfaces.os390.BatchJSEMObjectInterface
import com.experian.eda.framework.runtime.dynamic.*

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.google.gson.internal.LinkedTreeMap
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.*
import groovy.swing.SwingBuilder
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.lang3.StringUtils
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.lang3.time.DateUtils
import org.apache.commons.io.*

import groovy.transform.CompileStatic

import javax.management.MBeanServer
import javax.management.ObjectName
import javax.sql.DataSource
import java.sql.ResultSetMetaData
import java.sql.Statement
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

import org.statement.NamedPreparedStatement

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant

import static CustomUtils.*
import static DatabaseHelpers.*
import static RccDictionary.*
import static SmProcessingHelpers.*
import static ExtBatchSaveResultData.*
import static JsonProcessingHelpers.*
import static PADown.*
import static PACalc.*
import static ReadGlobalParams.*


import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import static com.experian.eda.enterprise.properties.PropertiesRegistry.*

@CompileStatic
class CustomUtils {

    static final Logger log = LoggerFactory.getLogger(this)
    public static String dbConnName, dbConnsPath, stageCd
    public static swingBuilder1 = new SwingBuilder()
    public static String statusLabel = null
    public static String[] custIds
    public static int traceFlags
    public static boolean csvOutput
    public static int batchThreads
    public static PADown paDown = null
    public static Thread thrDown = null
    public static PACalc paCalc = null
    public static Thread thrCalc = null
    public static int  batchQueueSize
    public static int fetchSize
    public static int rccDbCommitAfter
    public static int waveId = 0
    public static Long packGroupBy = 0
    public static int packId = 0
    public static Long batchId = 0
    static int tasksExecutedByCallerThreadCount=0
    public static final String RESTRUCT_STRATEGY_ALIAS = "PA_Restr"
    public static final String GLOBAL_PARAM_BATCH_STRATEGY_ALIAS = "BATCH_STRATEGY_ALIAS"
    public static final String GLOBAL_PARAM_BATCH_STRATEGY_SIGNATURE = "BATCH_STRATEGY_SIGNATURE"
    public static final String GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS = "BATCH_STRATEGY_TRACE_FLAGS"
    public static final String GLOBAL_PARAM_BATCH_THREADS = "BATCH_THREADS"
    public static final String GLOBAL_PARAM_BATCH_QUEUE_SIZE = "BATCH_QUEUE_SIZE"
    public static final String GLOBAL_PARAM_RCC_DB_COMMIT_AFTER = "RCC_DB_COMMIT_AFTER"
    public static final String GLOBAL_PARAM_RCC_DB_FETCH_SIZE = "RCC_DB_FETCH_SIZE"
    public static final long SM_THREAD_POOL_KEEP_ALIVE_TIME = 0 // Используются только основные потоки, поэтому 0

    public static final String STRATEGY_BASE_DIR = "strategies"

    public static final String SM_CONF_TABLE_NAME = "SM_CONF"
    public static final String SM_CONF_BATCH_SQL_TABLE_NAME = "SM_CONF_BATCH_SQL"
    public static final String SM_CONF_BATCH_MAP_TABLE_NAME = "SM_CONF_BATCH_MAP"
    public static final String SM_PROC_LOG_TABLE_NAME = "SM_PROC_LOG"
    public static final String SM_CONF_SELECT_QUERY =
            "select * from $SM_CONF_TABLE_NAME"
    public static final String SM_CONF_BATCH_SQL_SELECT_QUERY =
            "select ENTITY_NAME, PARENT_ENTITY_NAME, BIND_BIT_OFFSET, ARRAY_TYPE, SYNC_RESULTSET_COL, MAP_DIRECTION, TARGET_DB, SM_LAYOUT, CLEAR_ON_CALLS, SQL_TEXT from $SM_CONF_BATCH_SQL_TABLE_NAME"
    public static final String SM_CONF_BATCH_MAP_SELECT_QUERY =
            "select SM_FIELD, DB_FIELD, FIELD_TYPE, ENTITY_NAME, MAP_DIRECTION, DATA_TYPE from $SM_CONF_BATCH_MAP_TABLE_NAME order by ENTITY_NAME, MAP_DIRECTION"
    public static final String SM_CONF_CUSTOMERINFO_QUERY =
            "select  S.CUSTOMER_ID, S.STAGE_CD, S.PACK_ID, S.BATCH_ID, S.WAVE_ID, P.PACK_GROUP_BY from SPR_RESULT S inner join PACK P on P.PACK_ID = S.PACK_ID \n" +
                    "    where S.CUSTOMER_ID = :CUSTOMER_ID and S.STAGE_CD = :STAGE_CD"

    public static final String SM_FIELD_STAGE_ID = "STAGE_CD"
    public static final String SM_FIELD_WAVE_ID = "WAVE_ID"
    public static final String SM_FIELD_BATCH_ID = "BATCH_ID"
    public static final String SM_FIELD_PACK_ID = "PACK_ID"
    public static final String SM_FIELD_CUSTOMER_ID = "CUSTOMER_ID"
    public static final String SM_FIELD_PACK_GROUP_BY = "PACK_GROUP_BY"
    public static final String SM_FIELD_NEXT_SM_INPUT_ENTITY_CD = "NEXT_SM_INPUT_ENTITY_CD"
    public static final String SM_FIELD_DECISION = "DECISION"
    public static final String SM_FIELD_EDITION_NUMBER = "EDITION_NUMBER"
    public static final String SM_FIELD_EDITION_CREATION_DT = "EDITION_CREATION_DT"
    public static final String SM_FIELD_EDITION_SOFTWARE_VERSION = "SOFTWARE_VERSION"
    public static final String SM_FIELD_HOST_NAME = "HOST_NAME"
    public static final String SM_FIELD_START_TIME = "" +
            "TART_TIME"
    public static final String SM_FIELD_SPR_PROCESSING_STATUS = "SPR_PROCESSING_STATUS_CD"
    public static final String SM_FIELD_RKK_PROCESSING_STATUS = "RKK_PROCESSING_STATUS"
    public static final String SM_FIELD_ERROR_CODE = "ERROR_CD"
    public static final String SM_FIELD_ERROR_MSG = "ERROR_MSG"

    /*******************************************************************************************************
     * Константа хранит имя хоста текущего сервера
     *******************************************************************************************************/
    public static final String HOSTNAME = "ifrkkx-ap-localtester"

    public static final String SPR_PROCESSING_STATUS_OK = 0
    public static final String SPR_PROCESSING_STATUS_ERROR_NO_REPROCESS = 2 // Значение ошибки без авторепроцесса в ОКР
    public static final String SM_PROC_ERROR_CODE_DEF = 500
    public static final Integer RKK_PROCESSING_STATUS_INIT = 0

    public static final Long PACK_GROUP_BY_DEF_VAL = 0B11111111111111111111111111111111
    public static Map<String,Long> packGroupByFromInitCall = new HashMap<>()

    public static Map<String,Date> strategyCreationDateMap = new HashMap<>()
    public static Map<String,Integer> strategyEditionNumberMap = new HashMap<>()
    public static Map<String,String> strategySmSoftwareVersionMap = new HashMap<>()

    public static final String ENTITY_ARRAY_TYPE_STATIC = "STATIC"
    public static final String ENTITY_ARRAY_TYPE_DYNAMIC = "DYNAMIC"
    public static final String ENTITY_ARRAY_TYPE_TRANSPOSED = "TRANSPOSED"

    public static final String SQL_SELECT_PARAM_PACK_ID = "PACK_ID"
    public static final String SQL_SELECT_PARAM_STAGE_CD = "STAGE_CD"
    public static final String SQL_SELECT_PARAM_CUSTOMER_ID = "CUSTOMER_ID"
    public static final String SQL_SELECT_PARAM_WAVE_ID = "WAVE_ID"
    public static final String SQL_SELECT_PARAM_BATCH_ID = "BATCH_ID"

    public static final String SQL_TREE_ROOT = 'ROOT'
    public static final String SQL_TREE_OUTPUT_ROOT = "OUTPUT_ROOT"
    public static final String SQL_TREE_INPUT_ROOT = "INPUT_ROOT"

    public static final String SQL_ENTITY_CUSTOMER = "CUSTOMER"
    public static final String SQL_ENTITY_SPR_EXT_CALL_ROUTE = "EXT_CALL_ROUTE"

    public static final String SQL_LOAD_RCC_DICTIONARY = "DICTIONARY_ELEMENT"

    public static final int EVENT_PROCESS_BATCH_LOAD_SQL = 71       // Pack read SQL execution completed

    public static final String SM_LAYOUT_CUSTOMER = "CUSTOMER"
    public static final String SM_LAYOUT_STATE = "PA_STATE"
    public static final String SM_LAYOUT_DECISION = "DECISION_RESULT"
    public static final String SM_LAYOUT_TRIG_MONITORING_TASK = "TRIG_MONITORING_TASK"

    public static final String DEFAULT_CHARSET = "windows-1252"
    public static final String SM_PATH_ROOT_PATTERN = "@ROOT."
    public static final String INPUT_MAP_DIRECTION = "INPUT"
    public static final String OUTUT_MAP_DIRECTION = "OUTPUT"

    public static final int EVENT_DAPE_DONE = 100               //Завершение пула потоков стратегий Decision Agent Pool

    // Пул потоков для вызова стратегий
    public static ThreadPoolExecutor decisionAgentPoolExecutor
    public static AtomicBoolean stopSemaphore = new AtomicBoolean(false)
    public static AtomicBoolean packRunningSemaphore = new AtomicBoolean(false)

    // Контейнер для параметров глобальной конфигурации СПР
    public static HashMap<String, Object> globalConf = new HashMap<String, Object>()
    public static HashMap<String, HashMap<String, RccEntity>> globalConfSqlTreeInput = new HashMap<String, HashMap<String, RccEntity>>()
    public static HashMap<String, HashMap<String, RccEntity>> globalConfSqlTreeOutput = new HashMap<String, HashMap<String, RccEntity>>()
    public static HashMap<String, RccEntity> globalConfSqlFlatMapInput = new HashMap<String, RccEntity>()
    public static HashMap<String, RccEntity> globalConfSqlFlatMapOutput = new HashMap<String, RccEntity>()
    public static HashMap<String, ArrayList<HashMap<String, String>>> globalConfMapInput = new HashMap<String, ArrayList<HashMap<String, String>>>()
    public static HashMap<String, ArrayList<HashMap<String, String>>> globalConfMapOutput = new HashMap<String, ArrayList<HashMap<String, String>>>()
    public static HashMap<String, ArrayList<String>> clearableEntities = new HashMap<String, ArrayList<String>>()

    public static GsonBuilder gsonBuilder = new GsonBuilder()

    // Глобальные счётчики обработки пакетов
    public static AtomicInteger totalRecords = new AtomicInteger()
    public static AtomicInteger successfulRecords = new AtomicInteger()
    public static AtomicInteger failedRecords = new AtomicInteger()
    public static AtomicLong currentWaveId = new AtomicLong()
    public static AtomicLong currentBatchId = new AtomicLong()
    public static AtomicLong currentPackId = new AtomicLong()
    public static String currentStateCd = null

    public static AtomicInteger totalNumOfUpdatedRows = new AtomicInteger()

    public static String loadedStrategy = null
    public static final int ERR_INIT_CONF = 1000
    public static final int EVENT_INIT_STRATEGY = 20            // Инициализация стратегий
    public static final int ERR_INIT_STRATEGY = 2000
    public static final int EVENT_INIT_CONF = 10                // Инициализация конфигурации
    public static final int EVENT_DB_STAT = 4

    public static ArrayList<HashMap<String, String>> csvColumnsList = new ArrayList<HashMap<String, String>>() //Лист из колонок для вывода

    /*******************************************************************************************************
     * Метод возвращает значение глобального параметра СПР
     * Если значение аргумента throwExIfNull = true, метод выбрасывает исключение если параметр = null
     *******************************************************************************************************/
    static Object getGlobalParam(String paramName, boolean throwExIfNull) {
        try {
            Object retVal = globalConf.get(paramName)
            if (retVal == null) {
                retVal = globalConf.get(paramName.toLowerCase())
            }
            if (throwExIfNull && (retVal == null || retVal == ""))
                throw new Exception("Global PCSM configuration parameter '${paramName}' not found or empty")
            return retVal
        } catch (Exception e) {
            throw new Exception("Failed to read global PCSM configuration parameter '${paramName}': {$e}")
        }
    }

    /*******************************************************************************************************
     * Метод возвращает значение глобального параметра СПР
     *******************************************************************************************************/
    static Object getGlobalParam(String paramName) {
        return getGlobalParam(paramName, false)
    }

    /*******************************************************************************************************
     * Метод устанавливает значение глобального параметра СПР
     *******************************************************************************************************/
    static void setGlobalParam(String paramName, Object paramVal) {
        //log.info("Parameter $paramName = '$paramVal'")
        if (paramVal != null) {
            globalConf.put(paramName, paramVal)
        } else {
            globalConf.remove(paramName)
        }
    }

    static String[] getStringArrFromChar(String input){
        String[] out = null
        String outS = String.valueOf(input)
        out = outS.split(/\n/) as String[]
        return out
    }

    static long getDurationMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis()
    }

    /*******************************************************************************************************
     * Метод возвращает значение переменной окружения ОС
     *******************************************************************************************************/
    static String getEnvironmentVariable(String varName) {
        String varVal = System.getenv(varName)
        log.debug("getEnvironmentVariable: get $varName = $varVal")
        return varVal
    }

    /*******************************************************************************************************
     * Метод возвращает значение параметра из локального файла tenant.properties
     * Если значение аргумента throwExIfNull = true, метод выбрасывает исключение если параметр = null
     *******************************************************************************************************/
    static String getTenantProperty(String paramName, boolean throwExIfNull) {
        try {
            String retVal =  (String) getTenantProperties().get(paramName)
            log.info("Found tenant property '$paramName' = '$retVal'")
            if (throwExIfNull && (retVal == null || retVal == ""))
                throw new Exception("'${paramName}' not found or empty")
            return retVal
        } catch (Exception e) {
            throw new Exception("Unable to retrieve tenant property '${paramName}': {$e}")
        }
    }

    static String getHeapMemInfo() {
        int maxMemoryMb = Runtime.getRuntime().maxMemory()/ 1024 / 1024 as int
        int totalMemoryMb = Runtime.getRuntime().totalMemory()/ 1024 / 1024 as int
        int usedMemoryMb = (totalMemoryMb - Runtime.getRuntime().freeMemory() / 1024 / 1024 as int)
        return String.format("[MemInfoMb Xmx=%d, total=%d, used=%d]",maxMemoryMb, totalMemoryMb, usedMemoryMb)
    }

    /*******************************************************************************************************
     * Метод возвращает значение целочисленного параметра из локального файла tenant.properties
     * При ошибке конверсии значения параметра в число возвращается дефолтное значение из аргумента defaultValue
     *******************************************************************************************************/
    static Integer getTenantPropertyInt(String paramName, Integer defaultValue) {
        Integer retVal
        try {
            retVal = Integer.parseInt(getTenantProperty(paramName, false))
            return retVal
        } catch (Exception e) {
            log.warn("Failed to parse tenant property '$paramName': ${e.getMessage()}")
            retVal = defaultValue // default recommended value
            log.warn("'$paramName' set to default: $retVal")
            return retVal
        }
    }

    /*******************************************************************************************************
     * Метод возвращает значение зашифрованного параметра из локального файла tenant.properties
     * в расшифрованном виде.
     * Если значение аргумента throwExIfNull = true, метод выбрасывает исключение если параметр = null
     *******************************************************************************************************/
    static String getTenantPropertyEnc(String paramName, boolean throwExIfNull) {
        try {
            byte[] valBt = (byte[]) getTenantProperties().get(paramName)
            String retVal = new String(valBt, DEFAULT_CHARSET)
            log.info("Found tenant property '$paramName' = '*********'")
            if (throwExIfNull && (retVal == null || retVal == ""))
                throw new Exception("'${paramName}' not found or empty")
            return retVal
        } catch (Exception e) {
            throw new Exception("Unable to retrieve tenant property '${paramName}': {$e}")
        }
    }

    static String getSmAliasFromStageCd(String stageCd) {
        return "RESTRUCT".equalsIgnoreCase(stageCd) ? RESTRUCT_STRATEGY_ALIAS : getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_ALIAS, true)
    }

    /**
     * Выставляем значения traceFlags, batchThreads, batchQueueSize и rccDbCommitAfter из getGlobalParam
     */
    static void setPackProcessingOptions() {
        traceFlags = getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS, true) as Integer
        //traceFlags = 0
        batchThreads = getGlobalParam(GLOBAL_PARAM_BATCH_THREADS, true) as Integer
        batchQueueSize = getGlobalParam(GLOBAL_PARAM_BATCH_QUEUE_SIZE, true) as Integer
        fetchSize = getGlobalParam(GLOBAL_PARAM_RCC_DB_FETCH_SIZE, true) as Integer
        rccDbCommitAfter = getGlobalParam(GLOBAL_PARAM_RCC_DB_COMMIT_AFTER, true) as Integer
    }

    /*******************************************************************************************************
     * Коллекция всех используемых объектов PreparedStatement и связанных с ними ResultSet для получения данных РКК
     *******************************************************************************************************/
    public static Map<String, NamedPreparedStatement> packPreparedStatementsMap
    public static ConcurrentHashMap<String, ResultSet> packResultSetsMap

    /**
        * Пробуем взять из PCSM.SM_CONF актуальные значения traceFlags, batchThreads, batchQueueSize и rccDbCommitAfter для текущего пакета
    */
    static void updateSmConfValues() {
        Connection pcsmConn = getPcsmDbConnection()
        Statement st = null
        ResultSet smConfResultSet = null
        try {
            st = pcsmConn.createStatement()
            smConfResultSet = st.executeQuery(SM_CONF_SELECT_QUERY)
            ResultSetMetaData metadata = smConfResultSet.getMetaData()
            String[] params = [GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS, GLOBAL_PARAM_BATCH_THREADS, GLOBAL_PARAM_BATCH_QUEUE_SIZE, GLOBAL_PARAM_RCC_DB_COMMIT_AFTER, GLOBAL_PARAM_RCC_DB_FETCH_SIZE]
            if (smConfResultSet.next()) {
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String paramName = metadata.getColumnName(i)
                    boolean match = params.any {String it -> it.equalsIgnoreCase(paramName)}
                    if (match) {
                        Object paramVal = smConfResultSet.getObject(i)
                        setGlobalParam(paramName, paramVal)
                    }
                }
            }

            //сравним новые значения с getGlobalConf с ранее сохраненными значениями
            if (   getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS, true) as Integer != traceFlags
                    || getGlobalParam(GLOBAL_PARAM_BATCH_THREADS, true) as Integer != batchThreads
                    || getGlobalParam(GLOBAL_PARAM_BATCH_QUEUE_SIZE, true) as Integer != batchQueueSize
                    || getGlobalParam(GLOBAL_PARAM_RCC_DB_COMMIT_AFTER, true) as Integer != rccDbCommitAfter
                    || getGlobalParam(GLOBAL_PARAM_RCC_DB_FETCH_SIZE, true) as Integer !=  fetchSize )
            {
                setPackProcessingOptions()
                String logOptionsValues = String.format("Pack options values: $GLOBAL_PARAM_BATCH_THREADS=%d, $GLOBAL_PARAM_BATCH_QUEUE_SIZE=%d, $GLOBAL_PARAM_RCC_DB_COMMIT_AFTER=%d, $GLOBAL_PARAM_BATCH_STRATEGY_TRACE_FLAGS=%d, $GLOBAL_PARAM_RCC_DB_FETCH_SIZE=%d, ${getHeapMemInfo()}",
                        batchThreads, batchQueueSize, rccDbCommitAfter, traceFlags, fetchSize)
                log.info(logOptionsValues)
                log.info("TraceFlags = $traceFlags")
            }
        } catch (Exception e) {
            log.error("updateSmConfValues failed $e")
        } finally {
            pcsmConn.commit()
            smConfResultSet?.close()
            st?.close() //javadoc : When a Statement object is closed, its current ResultSet object, if one exists, is also closed
            pcsmConn?.close()
        }
    }

    static void setGlobalConfSqlEntity(String parentEntityName, String entityName, Integer bindBitOffset, String arrayType, String syncResultsetCol,
                                       String mapDirection, String targetDb, String smLayout, String clearOnCalls, String sqlText) {
        if (sqlText == null)
            throw new Exception("$SM_CONF_BATCH_SQL_TABLE_NAME validation failed: SQL_TEXT cannot be null for $entityName")
        log.info("Check 2")
        if (!parentEntityName?.trim()) parentEntityName = SQL_TREE_ROOT
        log.info("$mapDirection SQL $parentEntityName.$entityName = '$sqlText'")

        Map entityGroup = (mapDirection == OUTUT_MAP_DIRECTION ?
                globalConfSqlTreeOutput.get(parentEntityName) :
                globalConfSqlTreeInput.get(parentEntityName))
        if (entityGroup == null) entityGroup = new HashMap<String, RccEntity>()

        RccEntity rccEntity = new RccEntity(entityName, sqlText, bindBitOffset, arrayType, syncResultsetCol, smLayout, targetDb, mapDirection, clearOnCalls)
        /*try {
            ArrayList<String> clearOnCallsList = new ArrayList<String>()
            clearOnCallsList = clearOnCalls?.split(",") as ArrayList<String>

            clearOnCallsList?.each { clearCall ->
                ArrayList<String> entityList = new ArrayList<String>()
                if (clearableEntities.containsKey(clearCall)) {
                    clearableEntities.get(clearCall).add(entityName)
                } else {
                    entityList = new ArrayList<String>()
                    entityList.add(entityName)
                    clearableEntities.put(clearCall, entityList)
                }
            }
        } catch (Exception e) {
            String errorMsg = "Failed to fill clearable entities map: ${e.message}"
            log.error(errorMsg, e)
            throw new Exception(errorMsg)
        }*/
        entityGroup.put(entityName, rccEntity)
        if (mapDirection == OUTUT_MAP_DIRECTION) {
            globalConfSqlFlatMapOutput.put(entityName, rccEntity)
            globalConfSqlTreeOutput.put(parentEntityName, entityGroup)
            log.info("mapDirection = OUTUT_MAP_DIRECTION; FlatMap: entityname = $entityName, rccEntity = $rccEntity; Tree: parentEntityName = $parentEntityName, entityGroup = $entityGroup")
        } else {
            globalConfSqlFlatMapInput.put(entityName, rccEntity)
            globalConfSqlTreeInput.put(parentEntityName, entityGroup)
            log.info("mapDirection = INPUT_MAP_DIRECTION; FlatMap: entityname = $entityName, rccEntity = $rccEntity; Tree: parentEntityName = $parentEntityName, entityGroup = $entityGroup")
        }
    }

    static String getGlobalConfSqlInput(String parentEntityName, String entityName) {
        try {
            String retVal = globalConfSqlTreeInput.get(parentEntityName)?.get(entityName)?.sqlText
            if (!retVal?.trim())
                throw new Exception("Parameter not found or empty")
            return retVal
        } catch (Exception e) {
            throw new Exception("Failed to read global PCSM SQL parameter '$parentEntityName.$entityName': {$e}")
        }
    }

    static void clearExistingDbResults(Connection rccConn, String stageCd) {
        try {
            Instant startSql = Instant.now()
            StringJoiner strJoiner = new StringJoiner(', ', "Clear db results (entity:rows:ms): ", '')
            clearableEntities[stageCd]?.each { entity ->
                log.info("Purging DB entity for stage $stageCd: $entity")
                String entityClearLog = clearEntity("DELETE_${entity}", rccConn, getGlobalConfSqlInput(SQL_TREE_ROOT, "DELETE_${entity}"))
                strJoiner.add(entityClearLog)
            }
            clearableEntities["ANY"]?.each { entity ->
                log.info("Purging DB entity for stage ANY: $entity")
                String entityClearLog = clearEntity("DELETE_${entity}", rccConn, getGlobalConfSqlInput(SQL_TREE_ROOT, "DELETE_${entity}"))
                strJoiner.add(entityClearLog)
            }
            String allEntitiesClearLog = strJoiner.toString()
            //logEventInfo(allEntitiesClearLog, getDurationMs(startSql), EVENT_EXEC_SINGLE_BATCH_LOAD_SQL, currentPackId.get() as Long, null, log)
            log.info(allEntitiesClearLog)
            rccConn.commit() // Иначе будет висеть блокировка в БД
        } catch (Exception e) {
            rccConn.rollback()
            throw e
        }
    }

    static RccEntity getGlobalConfSqlEntity(String parentEntityName, String entityName, String mapDirection) {
        try {
            RccEntity retVal = (mapDirection == OUTUT_MAP_DIRECTION ?
                    globalConfSqlTreeOutput.get(parentEntityName)?.get(entityName) :
                    globalConfSqlTreeInput.get(parentEntityName)?.get(entityName))
            if (retVal == null) throw new Exception("Parameter not found or empty")
            return retVal
        } catch (Exception e) {
            throw new Exception("Failed to read global PCSM SQL parameter '$parentEntityName.$entityName': {$e}")
        }
    }

    static RccEntity getGlobalConfSqlEntity(String parentEntityName, String entityName) {
        getGlobalConfSqlEntity(parentEntityName, entityName, null)
    }

    static void loadStrategy(String smAlias) {
        /********************************************************************************
         *  Инициализация стратегий
         ********************************************************************************/
        log.info("Loading strategy '$smAlias'")
        Instant startStrategyLoad = Instant.now()

        //Throw exception if strategy alias jar file doesn't exist
        String strategyJarFilePath = "$STRATEGY_BASE_DIR/${smAlias}.jar"
        log.info(strategyJarFilePath)
        File jarFile = new File (strategyJarFilePath)
        if (!jarFile.exists()) throw new SmInitException("Failed to load strategy $smAlias: ${smAlias}.jar not exists")

        //If ser file exists, delete it. Then unpack new ser file from the actual jar file.
        String strategySerFilePath = "$STRATEGY_BASE_DIR/${smAlias}.ser"
        File serFile = new File(strategySerFilePath)
        if (serFile.exists()) FileUtils.forceDelete(serFile)
        unzipStrategy(strategyJarFilePath, smAlias, STRATEGY_BASE_DIR)

        Boolean isStrLoaded = DAManagementInterface.isStrategyLoaded(smAlias)
        log.info("isStrLoaded = $isStrLoaded")
        boolean strAlreadyLoaded = isStrLoaded
        String resultCode = null
        if (!isStrLoaded) {
            resultCode = DAManagementInterface.loadStrategy(smAlias)
            isStrLoaded = true
        }

        String strategyInfo = DAManagementInterface.getStrategyInfo(smAlias)
        if (isStrLoaded || !strategyInfo?.trim()) {
            log.info("'$smAlias' strategy loaded successfully. Strategy info: [${strategyInfo}]")
            loadedStrategy = smAlias;
        } else throw new SmInitException("Failed to load strategy '$smAlias'. resultCode: [$resultCode] strategyInfo: [$strategyInfo]")
        log.info("Strategy '$smAlias' successfully loaded")

        /********************************************************************************
         *  Извлечение Creation date и Edition number
         ********************************************************************************/

        if (!strAlreadyLoaded) {
            try {
                IRuntimeProperties strategyProperties = StrategyCache.getInstance().getStrategyProperties(smAlias)
                strategyCreationDateMap.put(smAlias,strategyProperties.creationDate)
                log.debug("creationDate = ${strategyProperties.creationDate}")
                strategyEditionNumberMap.put(smAlias,strategyProperties.editionNumber)
                log.debug("editionNumber = ${strategyProperties.editionNumber}")
                strategySmSoftwareVersionMap.put(smAlias,strategyProperties.smSoftwareVersion)
                log.debug("strategySmSoftwareVersionMap = ${strategyProperties.smSoftwareVersion}")
            } catch (Exception e) {
                String errorMsg = "Failed to extract '$smAlias' srtategy metadata: $e"
                log.error(errorMsg, e)
                throw new SmInitException(errorMsg)
            }
        }
    }

    /*******************************************************************************************************
     * Метод распаковывает .jar файл стратегии
     *******************************************************************************************************/
    static void unzipStrategy(String jarFile, String alias, String destDir) {
        String srcFileName = "$destDir/${alias}.jar"
        String destFileName = "$destDir/${alias}.ser"
        log.info(srcFileName)
        log.info(destFileName)
        if (jarFile != srcFileName) throw new Exception("Strategy .jar file differs from the strategy alias supplied")
        boolean serFileExtracted = false
        JarFile jar = new JarFile(jarFile)
        log.info("JarFile jar")
        try {
            Enumeration enumEntries = jar.entries()
            while (enumEntries.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumEntries.nextElement()
                if (jarEntry.getName() != "${alias}.ser" || jarEntry.isDirectory()) continue
                File serFile = new File(destFileName)
                InputStream is = null
                FileOutputStream fos = null
                try {
                    is = jar.getInputStream(jarEntry); // get the input stream
                    fos = new FileOutputStream(serFile)
                    IOUtils.copy(is, fos);
                    serFileExtracted = true
                    log.info("serFileExtracted")
                } finally {
                    fos?.close();
                    is?.close();
                }
            }
        } finally {
            jar.close()
        }
        if (!serFileExtracted) throw new Exception("JAR file '$jarFile' does not contain expected strategy file '${alias}.ser'")
    }

    static void logFatalErrorAndShutdown(String msg, Integer eventType, Integer errorCode, Logger logger, Exception e) {
        try {
            log.info(msg)
        } catch (all) {
        }
        System.exit(0)
    }

}

@CompileStatic
class RccEntity {
    public String sqlText
    public String entityName
    public Integer bindBitOffset
    public String arrayType
    public Long bindBit
    public String syncResultsetCol
    public String smLayoutName
    public Integer targetDb
    public String direction
    public String clearOnCalls

    public static final int RCC_DB = 0
    public static final int PCSM_DB = 1

    RccEntity(String entityName, String sqlText, Integer bindBitOffset, String arrayType, String syncResultsetCol, String smLayoutName, String targetDb, String direction, String clearOnCalls) {

        this.entityName = entityName
        this.sqlText = sqlText
        this.bindBitOffset = bindBitOffset
        this.arrayType = arrayType
        this.bindBit = (bindBitOffset ? Math.pow(2, bindBitOffset).longValue() : null)
        this.syncResultsetCol = syncResultsetCol
        this.smLayoutName = smLayoutName
        this.direction = direction
        this.clearOnCalls = clearOnCalls
        switch (targetDb) {
            case "RCC": this.targetDb = RCC_DB; break;
            case "PCSM": this.targetDb = PCSM_DB; break;
            default: this.targetDb = null
        }
    }

    String getSqlText() {
        return sqlText
    }

    String getEntityName() {
        return entityName
    }

    Integer getBindBitOffset() {
        return bindBitOffset
    }

    Long getBindBit() {
        return bindBit
    }

    String getClearOnCalls() {
        return clearOnCalls
    }

}

@CompileStatic
class DatabaseHelpers {
    private static final Logger log = LoggerFactory.getLogger(this)

    public static DataSource rccDataSource = null
    public static DataSource pcsmDataSource = null
    public static Properties pcsmDBprops = new Properties()
    public static MBeanServer mBeanServer = null
    public static ObjectName poolName = null
    public static HealthCheckRegistry dbHealth = new HealthCheckRegistry()
    public static MetricRegistry dbMetrics = new MetricRegistry()
    //public dbConnName = ""
    //public String pathName = "dbConns/$dbConnName"
    //public File pcsmDB = new File(pathName)

    public static final int ORA_RECONNECT_INTERVAL_MS = 30000 // Интервал ожидания перед повторным подключением к БД
    public static final int ORA_RECONNECT_MAX_ATTEMPTS = getTenantPropertyInt("oracle.connection.retry.attempts", 20)
    // Кол-во попыток повторного подключения к БД
    public static final int ORA_RECONNECT_MAX_TIME = getTenantPropertyInt("oracle.connection.retry.maxTimeMs", 180000)
    // Максимальное время повторного подключения к БД (в миллисекундах)
    public static final int ORA_MAX_STMT_CACHE = 6000          // Размера кеша запросов пула коненктов
    public static final int ORA_INIT_CONN_POOL_SIZE = 3     // Начальный размера пула
    public static final int ORA_MIN_CONN_POOL_SIZE = 3      // Мин. размера пула
    public static final int ORA_MAX_CONN_POOL_SIZE = 20     // Макс. размера пула
    public static final int ORA_ABANDON_CONN_TIMEOUT_SEC = 600

    static Connection getConnection(DataSource dataSource) {
        log.info("Getting connection...")
        //dataSource = dataSource as BasicDataSource
        if (dataSource == null) throw new Exception("Error: data source is not created, view logs for details")
        int i = 0
        Instant startConnecting = Instant.now()
        while (true) {
            try {
                i++;
                Connection conn = dataSource.getConnection()
                conn.setAutoCommit(false)
                String schema = ((HikariDataSource)dataSource).getSchema()
                if (schema?.trim()) {
                    conn.setSchema(schema)
                }
                log.info("Successfully get connection")
                return conn

            } catch (Exception e) {
                log.error("Failed [retries : ${i}, time elapsed (ms) : ${getDurationMs(startConnecting)}] to open conection: ${e.getMessage()}")

                String oraErrorNumber = null
                boolean authError = false
                if (e.getMessage()?.contains("SQLException: ORA-")) {
                    oraErrorNumber = StringUtils.substringBetween(e.getMessage(), "SQLException: ", ": ");
                }
                // Проверка кол-ва попыток, времени и "ORA-01017: invalid username/password" или "password authentication failed for user в psql"
                // - чтобы не лочить учётку повторными попытками
                if (e.getMessage()?.contains("password authentication failed for user") || oraErrorNumber == 'ORA-01017') {
                    authError = true
                }
                if (i > ORA_RECONNECT_MAX_ATTEMPTS || getDurationMs(startConnecting) > ORA_RECONNECT_MAX_TIME || authError) throw e
                else {
                    log.error("Wait for $ORA_RECONNECT_INTERVAL_MS ms and retry...")
                    sleep(ORA_RECONNECT_INTERVAL_MS) // to make retries with an interval
                }
            }
        }
    }

    static Connection getConnection(DataSource dataSource, boolean enableAutoCommit) {
        Connection conn = getConnection(dataSource)
        conn.setAutoCommit(enableAutoCommit)
        return conn
    }

    static Connection getRccDbConnection() {
        return getConnection(rccDataSource)
    }

    static Connection getPcsmDbConnection() {
        return getConnection(pcsmDataSource)
    }
    static String clearEntity(String entityName, Connection connection, String updateSql) {
        Instant startSql = Instant.now()
        log.info("Executing clearEntity command '$updateSql'")
        NamedPreparedStatement ps = new NamedPreparedStatement(connection, updateSql)
        ps.setLongAtName(SQL_SELECT_PARAM_PACK_ID, currentPackId.get())
        ps.setLongAtName(SQL_SELECT_PARAM_BATCH_ID, currentBatchId.get())
        int recAffected = ps.executeUpdate()
        log.info("clearEntity command executed, $recAffected records deleted'")
        long ms = getDurationMs(startSql)
        return "$entityName:$recAffected:$ms"
    }

    static NamedPreparedStatement getSmInputStatement(Connection conn, String parentEntityName, String entityName, boolean scrollable) {
        NamedPreparedStatement ps
        if (scrollable) ps = new NamedPreparedStatement(conn, getGlobalConfSqlInput(parentEntityName, entityName), ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)
        else ps = new NamedPreparedStatement(conn, getGlobalConfSqlInput(parentEntityName, entityName), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
        ps.setFetchSize(fetchSize)
        return ps
    }

    static boolean safeCloseConnection(Connection conn) {
        if (conn == null || conn.isClosed()) return false
        conn.close()
        return true
    }

    static void setDataSourceProperties(BasicDataSource dataSource) {
        dataSource.setInitialSize(ORA_INIT_CONN_POOL_SIZE);
        dataSource.setMinIdle(ORA_MIN_CONN_POOL_SIZE)
        dataSource.setMaxTotal(ORA_MAX_CONN_POOL_SIZE)
        dataSource.setMaxOpenPreparedStatements(ORA_MAX_STMT_CACHE)
        dataSource.setRemoveAbandonedOnMaintenance(true)
        dataSource.setRemoveAbandonedTimeout(ORA_ABANDON_CONN_TIMEOUT_SEC)
        if (dataSource.getUrl().contains("oracle")) {
            dataSource.setValidationQuery("SELECT 1 FROM DUAL")
        } else {
            dataSource.setValidationQuery("SELECT 1")
        }
    }

    static DataSource createDataSource(String poolName, String url, String user, String pass, String schema, String driverClassName) {
        log.info("Creating data source with custom URL and DriverClass $driverClassName")
        log.info("Connection URL: $url")
        /*BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(driverClassName)
        dataSource.setUrl(url)
        dataSource.setUsername(user)
        dataSource.setPassword(pass)
        if (schema?.trim()) {
            dataSource.setDefaultSchema(schema)
        }
        setDataSourceProperties(dataSource)*/
        HikariConfig hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName(driverClassName)
        hikariConfig.setJdbcUrl(url)
        hikariConfig.setUsername(user)
        hikariConfig.setPassword(pass)
        hikariConfig.setMaximumPoolSize(100)
        hikariConfig.setConnectionTimeout(5000)
        hikariConfig.setAutoCommit(false)
        hikariConfig.setPoolName(poolName)
        hikariConfig.registerMbeans = true
        hikariConfig.setMinimumIdle(5)
        hikariConfig.setMaxLifetime(6000)
        hikariConfig.setConnectionTestQuery("select 1")
        hikariConfig.setIdleTimeout(5000)
        hikariConfig.setLeakDetectionThreshold(60000)
         hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        if (schema?.trim()) {
            hikariConfig.setSchema(schema)
        }
        log.info("Successfully created data source with Driver Class $driverClassName")
        return new HikariDataSource(hikariConfig)
    }

    static DataSource createDataSourceWithFailover(String host1, String port1, String host2, String port2, String user, String pass,
                                                   String dbServiceName, String schema, String driverClassName) {
        log.info("Creating data source with failover...")

        String url = "";
        if (driverClassName.contains("oracle")) {
            url = """jdbc:oracle:thin:@(DESCRIPTION=
                       (FAILOVER=on)
                       (ADDRESS_LIST=
                          (LOAD_BALANCE=off)
                          (ADDRESS=(PROTOCOL=TCP)(HOST=$host1)(PORT=$port1))
                          (ADDRESS=(PROTOCOL=TCP)(HOST=$host2)(PORT=$port2)))
                       (CONNECT_DATA=(SERVICE_NAME=$dbServiceName)))"""
        } else {
            //jdbc:postgresql://host1:port1,host2:port2/database
            url = "jdbc:postgresql://$host1:$port1,$host2:$port2/$dbServiceName"
        }
        log.info("Connection URL: $url")
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(driverClassName)
        dataSource.setUrl(url)
        dataSource.setUsername(user)
        dataSource.setPassword(pass)
        if (schema?.trim()) {
            dataSource.setDefaultSchema(schema)
        }
        setDataSourceProperties(dataSource)
        log.info("Successfully created data source with Driver Class $driverClassName")
        return dataSource
    }

    static DataSource createDataSource(
            String host, String port, String user, String pass, String dbServiceName, String schema, String driverClassName) {
        BasicDataSource dataSource = new BasicDataSource()
        String url;
        if (driverClassName.contains("oracle")) {
            url = "jdbc:oracle:thin:@$host:$port/$dbServiceName"
        } else {
            //jdbc:postgresql://host1:port1,host2:port2/database
            url = "jdbc:postgresql://$host:$port/$dbServiceName"
        }
        log.info("Creating data source...")
        dataSource.setDriverClassName(driverClassName)
        dataSource.setUsername(user)
        dataSource.setPassword(pass)
        dataSource.setUrl(url)
        if (schema?.trim()) {
            log.info("Set schema")
            dataSource.setDefaultSchema(schema)
        }
        setDataSourceProperties(dataSource)
        log.info("Successfully created data source with Driver Class $driverClassName")
        return dataSource
    }

}

@CompileStatic
class RccDictionary {
    private static final Logger log = LoggerFactory.getLogger(this)
    private static Map<String, HashBiMap<Integer, String>> rccDictionaryElements = null
    private static Long waveId = 1

    static void addElement(String dictionaryCd, Integer rkkCd, String sprCd) {
        log.debug("Add RCC dictionary element: DICTIONARY_CD = $dictionaryCd, RKK_CD = $rkkCd, SPR_CD = $sprCd")
        BiMap<Integer, String> dictionary = rccDictionaryElements.get(dictionaryCd)
        if (dictionary == null) {
            dictionary = HashBiMap.create()
            rccDictionaryElements.put(dictionaryCd, dictionary)
        } else {
            if (dictionary.get(rkkCd) != null) {
                log.warn("Key RKK_CD = [$sprCd] already exists in dictionary [$dictionaryCd]")
                return
            }
            if (dictionary.inverse().get(sprCd) != null) {
                log.warn("Value SPR_CD = [$sprCd] already exists in dictionary [$dictionaryCd] for key RKK_CD = [$rkkCd]")
                return
            }
        }
        dictionary.put(rkkCd, sprCd)
    }

    static void loadRccDictionaryElementsFromDb(Connection rccConn, Long waveId) {
        if (this.waveId == waveId) {
            /*if (getTenantProperty("dictionary.reload.beforeAnyPack", false) == "true") {
                log.warn("Loading Rcc Dictionary: same WAVE_ID $waveId as already loaded cache. However, forced reload mode activated - continue with loading $SQL_LOAD_RCC_DICTIONARY")
            } else {*/
            //убрана проверка с тенанта, так как не подгружает проперти
            log.info("$SQL_LOAD_RCC_DICTIONARY has already been loaded for wave $waveId. Skip loading...")
                return
            //}
        }

        log.info("Clean up current Rcc Dictionary cache...")
        rccDictionaryElements = new HashMap<String, HashBiMap<Integer, String>>()
        log.info("Clean up current Rcc Dictionary cache - Done")

        log.debug("Preparing $SQL_LOAD_RCC_DICTIONARY select statement... ")
        NamedPreparedStatement psDict = null
        ResultSet rsDict = null
        String dictionaryCd = null
        try {
            psDict = getSmInputStatement(rccConn, SQL_TREE_ROOT, SQL_LOAD_RCC_DICTIONARY, false)
            psDict.setLong(1, waveId)
            rsDict = psDict.executeQuery()
            log.debug("rsDict ${rsDict.toString()}...")
            log.debug("Done! Loading $SQL_LOAD_RCC_DICTIONARY...")
            while (rsDict.next()) {
                dictionaryCd = rsDict.getString("DICTIONARY_CD")
                addElement(dictionaryCd, rsDict.getInt("RKK_CD"), rsDict.getString("SPR_CD"))
            }
            this.waveId = waveId
            log.info("Successfully loaded $SQL_LOAD_RCC_DICTIONARY")
            log.debug("rccDictionaryElements = $rccDictionaryElements")
        } catch (Exception e) {
            throw new Exception("Failed to load $SQL_LOAD_RCC_DICTIONARY for dictionary '$dictionaryCd': ${e.getMessage()}")
        } finally {
            rsDict?.close()
            psDict?.close()
        }
    }

    static Object decodeRccToSpr(String dictionaryCd, Integer rkkCd) {
        if (rkkCd == null) return null
        HashBiMap<Integer, String> dictionaryMap = rccDictionaryElements.get(dictionaryCd)
        if (dictionaryMap == null) {
            throw new SmCallException("Dictionary [$dictionaryCd] not found. RKK_CD value [$rkkCd] cannot be decoded to SPR_CD value")
        }
        String retVal = dictionaryMap.get(rkkCd)
        if (retVal == null) {
            throw new SmCallException("SPR_CD value not found in dictionary [$dictionaryCd] for RKK_CD value [$rkkCd]")
        }
        log.info("RKK_CD value [$rkkCd] decoded to SPR_CD value [$retVal] based on dictionary [$dictionaryCd]")
        return retVal
    }

}

@CompileStatic
class SmProcessingHelpers {
    private static final Logger log = LoggerFactory.getLogger(this)
    static long dataDepth

    static class MyThreadPoolExecutor extends ThreadPoolExecutor {

        private int maxQ = 0

        int getMaxQ() {
            return maxQ
        }

        MyThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler)
        }

        @Override
        void execute(Runnable command) {
            int initQSize = this.getQueue().size()
            super.execute(command)
            int currQSize = this.getQueue().size()
            if(currQSize > initQSize) { //таск был добавлен в очередь (хотя другой(ие) таски могли быть забраны воркерами...
                if (currQSize > maxQ) maxQ = currQSize
            }
        }
    }

    static class MyRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) { // slow down the rate that new tasks are submitted.
                r.run();
                tasksExecutedByCallerThreadCount++
            }
        }
    }

    static ThreadPoolExecutor createDecisionAgentPool(int batchThreads, int batchQueueSize) {
        // See https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html

        ThreadPoolExecutor daPoolExecutor =  new MyThreadPoolExecutor(
                batchThreads,           // corePoolSize
                batchThreads,           // maximumPoolSize
                SM_THREAD_POOL_KEEP_ALIVE_TIME, // By default, the keep-alive policy applies only when there are more than corePoolSize threads
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(batchQueueSize), //  helps prevent resource exhaustion when used with finite maximumPoolSizes
                new ThreadFactoryBuilder().setNameFormat("SM-Caller-%d").build(),
                new MyRejectedExecutionHandler()
        )

        return daPoolExecutor
    }

    static void setRccSelectQueryParams(NamedPreparedStatement ps, Long customerId, Long packId, String stageCd) {
        log.debug("Setting query parameters:\nwaveId = ${currentWaveId.get()}\npackId = $packId\nstageCd = $stageCd\ncustomerId = $customerId")
        ps.setLongAtName(SQL_SELECT_PARAM_PACK_ID, packId)
        ps.setStringAtName(SQL_SELECT_PARAM_STAGE_CD, stageCd)
        ps.setBigDecimalAtName(SQL_SELECT_PARAM_CUSTOMER_ID, customerId != null ? new BigDecimal(customerId) : null)
        ps.setLongAtName(SQL_SELECT_PARAM_WAVE_ID, currentWaveId.get())
        ps.setLongAtName(SQL_SELECT_PARAM_BATCH_ID, currentBatchId.get())
    }

    static void setRccSelectQueryParams(NamedPreparedStatement ps, Long customerId, Long stageCd) {
        log.debug("Setting query parameters:\nstageCd = $stageCd\ncustomerId = $customerId")
        ps.setLongAtName(SQL_SELECT_PARAM_STAGE_CD, stageCd)
        ps.setLongAtName(SQL_SELECT_PARAM_CUSTOMER_ID, customerId != null ? customerId : null)
    }

    static void openRccRootStatement(Connection conn, String entityName, Long customerId, Long packId, String stageCd) {
        log.debug("Preparing $entityName select statement... ")
        NamedPreparedStatement ps = getSmInputStatement(conn, SQL_TREE_INPUT_ROOT, entityName, false)
        setRccSelectQueryParams(ps, customerId, packId, stageCd)
        packPreparedStatementsMap.put(entityName, ps)
        log.debug("Done!")
    }

    static void loadCustomerInfo(Connection rccConn, Long customerId, Long StageCdInt, String StageCdStr) {
        log.debug("Preparing CustomerInfo select statement... ")
        NamedPreparedStatement ps
        ps = new NamedPreparedStatement(rccConn, SM_CONF_CUSTOMERINFO_QUERY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
        ps.setFetchSize(fetchSize)
        setRccSelectQueryParams(ps, customerId, StageCdInt)
        //packPreparedStatementsMap.put(SQL_ENTITY_CUSTOMER, ps)
        ResultSet rsCust = ps.executeQuery()
        log.info("rsCust = ${rsCust.toString()}")
        String smAlias = getSmAliasFromStageCd(StageCdStr)
        while (rsCust.next()) {
            packId = rsCust.getInt("PACK_ID")
            waveId = rsCust.getInt("WAVE_ID")
            packGroupBy = rsCust.getLong("PACK_GROUP_BY")
            //Если CLD1, RESTRUCT или (MIN_REQ и packGroupBy = 0 (null)) то пересчитываем packGroupBy
            if (((packGroupBy == null || packGroupBy ==0) && StageCdInt == 1) || (StageCdInt == 4 || StageCdInt == 25)) {
                loadStrategy(smAlias)
                executeInitCall(smAlias)
                packGroupBy = packGroupByFromInitCall.get(smAlias)
            }
            batchId = rsCust.getInt("BATCH_ID")
            log.info("CustomerInfo custId: ${rsCust.getLong("CUSTOMER_ID")}, packId = $packId, waveId = $waveId, packGroupBy = $packGroupBy")
        }
        rsCust?.close()
        ps?.close()

        log.debug("Done!")
    }

    static void openChildInputStatements(Connection rccConn, Connection pcsmConn, Long customerId, Long packId, String stageCd, Long packGroupBy, String parentEntityName, boolean scrollable, boolean ignorePackGroupBy) {
        globalConfSqlTreeInput.get(parentEntityName)?.each {
            childEntityName, rccEntity ->
                if (ignorePackGroupBy || checkEntityIsActive(rccEntity, packGroupBy, stageCd)) {
                    /*******************************************************************************************************
                     * Получаем все записи сущности, если она активна
                     *******************************************************************************************************/
                    log.info("Entity [$childEntityName] - proceed to SQL preparation (parentEntityName = $parentEntityName, childEntityName = $childEntityName)...")
                    Connection connection = (rccEntity.targetDb == RccEntity.RCC_DB ? rccConn : pcsmConn)

                    /*MBeanServer mBeanServerREACT = ManagementFactory.getPlatformMBeanServer();
                    poolName = new ObjectName("com.zaxxer.hikari:type=Pool (rcc-pool)")
                    HikariPoolMXBean poolProxyREACT = JMX.newMXBeanProxy(mBeanServerREACT, poolName, HikariPoolMXBean.class)
                    String infoMsg = String.format("REACT DB STAT - Active %d, Idle %d, Total %d, ThreadAwait %d", poolProxyREACT.getActiveConnections(), poolProxyREACT.getIdleConnections(),
                            poolProxyREACT.getTotalConnections(), poolProxyREACT.getThreadsAwaitingConnection())
                    log.info(infoMsg)*/
                    /*******************************************************************************************************
                     * Проверяем, что сущность без связи по syncResultsetCol, либо имеет такого родителя.
                     * В таком случае курсор надо открывать в режиме scrollable (для перемотки на первую запись после каждого прохода)
                     *******************************************************************************************************/
                    boolean thisScrollable = (rccEntity.syncResultsetCol == null || scrollable)

                    /*******************************************************************************************************
                     * Открываем курсор
                     *******************************************************************************************************/
                    NamedPreparedStatement ps = getSmInputStatement(connection, parentEntityName, childEntityName, thisScrollable)

                    log.debug("Entity [$childEntityName] - PreparedStatement ready")
                    setRccSelectQueryParams(ps, customerId, packId, stageCd)
                    packPreparedStatementsMap.put(childEntityName, ps)

                    openChildInputStatements(rccConn, pcsmConn, customerId, packId, stageCd, packGroupBy, childEntityName, thisScrollable, ignorePackGroupBy)
                   // (rccEntity.targetDb == RccEntity.RCC_DB ?
                     //       ((HikariDataSource)rccDataSource).evictConnection(connection) :
                     //       ((HikariDataSource)pcsmDataSource).evictConnection(connection))

                   // connection.close()
                } else {
                    log.info("Entity [$childEntityName] identified as inactive - skipping SQL execution")

                }
        }
    }

    static void openChildInputStatements(Connection rccConn, Connection pcsmConn, Long customerId, Long packId, String stageCd, Long packGroupBy, String parentEntityName, boolean scrollable) {
        openChildInputStatements(rccConn, pcsmConn, customerId, packId, stageCd, packGroupBy, parentEntityName, scrollable, false)
    }

    /*******************************************************************************************************
     * Проверка активности сущности в input-маппинге СПР для данного пакета
     *******************************************************************************************************/
    static boolean checkEntityIsActive(RccEntity rccEntity, Long packGroupBy) {
        Long entityBindBit = rccEntity.getBindBit() // Битовая маска сущности для проверки активности
        log.info("Checking [${rccEntity.getEntityName()}] entity bit '$entityBindBit' against packGroupBy ($packGroupBy):")
        Long entityCheck = (entityBindBit & packGroupBy) as Long
        log.debug("${Long.toBinaryString(packGroupBy)} & ${Long.toBinaryString(entityBindBit)} = ${Long.toBinaryString(entityCheck)}")
        boolean retVal = entityCheck > 0
        log.info("Entity [${rccEntity.getEntityName()}] is ${(retVal ? "ENABLED" : "DISABLED")}")
        return entityCheck > 0
    }

    static boolean checkEntityIsActive(RccEntity rccEntity, Long packGroupBy, String stageCd) {
        Long entityBindBit = rccEntity.getBindBit() // Битовая маска сущности для проверки активности
        log.info("Checking [${rccEntity.getEntityName()}] entity bit '$entityBindBit' against packGroupBy ($packGroupBy):")
        Long entityCheck = (entityBindBit & packGroupBy) as Long
        log.debug("${Long.toBinaryString(packGroupBy)} & ${Long.toBinaryString(entityBindBit)} = ${Long.toBinaryString(entityCheck)}")
        boolean retVal = entityCheck > 0
        String rccClearOnStage = rccEntity.getClearOnCalls()
        if (rccClearOnStage == stageCd || rccClearOnStage == "ANY") retVal = false //если сушность должна быть пустая на вызове то ее не берем
        log.info("rccClearOnStage = $rccClearOnStage, stageCd = $stageCd")
        log.info("Entity [${rccEntity.getEntityName()}] is ${(retVal ? "ENABLED" : "DISABLED")}")
        //return entityCheck > 0
        return retVal
    }

    static void refreshPcsmOnlyStatements(Long customerId, Long packGroupBy, String parentEntityName) {
        globalConfSqlTreeInput.get(parentEntityName)?.each {
            childEntityName, rccEntity ->
                /*******************************************************************************************************
                 * Получаем все записи сущности PCSM_DB, если она активна
                 *******************************************************************************************************/
                if (rccEntity.targetDb == RccEntity.PCSM_DB && checkEntityIsActive(rccEntity, packGroupBy)) {
                    log.info("Entity [$childEntityName] - proceed to SQL execution (parentEntityName = $parentEntityName, childEntityName = $childEntityName)...")
                    /*******************************************************************************************************
                     * Получим и закроем старый ResultSet по предыдущему Customer`у
                     *******************************************************************************************************/
                    ResultSet rs = packResultSetsMap.get(childEntityName)
                    rs?.close()

                    /*******************************************************************************************************
                     * Обновим PreparedStatement по новому customerId
                     *******************************************************************************************************/
                    NamedPreparedStatement ps = packPreparedStatementsMap.get(childEntityName)
                    log.debug("Entity [$childEntityName] - PreparedStatement open. Getting ResultSet for Customer $customerId...")
                    ps.setLongAtName(SQL_SELECT_PARAM_CUSTOMER_ID, customerId)
                    rs = ps.executeQuery()
                    packResultSetsMap.put(childEntityName, rs)
                    log.debug("Entity [$childEntityName] - ResultSet open successfully")

                    refreshPcsmOnlyStatements(customerId, packGroupBy, childEntityName)
                } else {
                    log.info("Entity [$childEntityName] identified as non-local or inactive - skipping SQL execution")
                }
        }
    }

    static void mapDbRecordToSm(ResultSet rs, Map<String, IHData> smLayoutsMap, RccEntity rccEntity, Integer index, String pathPrefix, boolean omitEntityName) {

        String entityName = rccEntity.entityName
        IHData smLayout = smLayoutsMap.get(rccEntity.smLayoutName)
        ArrayList<HashMap<String, String>> mapConf = globalConfMapInput.get(entityName)
        if (mapConf == null || mapConf?.size() < 1) {
            log.warn("No SM mapping found in SM_CONF_BATCH_MAP table for entityName [$entityName] - SKIP")
            return
        }

        switch (rccEntity.arrayType) {
            case ENTITY_ARRAY_TYPE_TRANSPOSED:
                String smFieldName = null
                Object smVal = null
                for (Map<String, String> mapEntry : globalConfMapInput.get(entityName)) {
                    String dbFieldName = mapEntry.get("DB_FIELD")
                    String dbFieldType = mapEntry.get("FIELD_TYPE")
                    switch (dbFieldType) {
                        case "KEY":
                            smFieldName = "$pathPrefix$entityName." + rs.getString(dbFieldName)
                            break
                        case "VALUE":
                            smVal = rs.getObject(dbFieldName)
                            break
                        default:
                            log.debug("Unknown mapping for Customer Flag")
                    }
                }
                if (smFieldName != null) {
                    setSmVal(smLayout, smFieldName, smVal)
                    String val = smVal.toString()
                }
                log.debug("Passed Customer Flag ==> SM value '${smLayout.getLayout()}.$smFieldName': '$smVal'")
                break
            default:
                for (Map<String, String> mapEntry : globalConfMapInput.get(entityName)) {
                    String smFieldName = mapEntry.get("SM_FIELD")
                    if (index != null) {
                        // Вложенная сущность - массив
                        smFieldName = "$pathPrefix$entityName[$index].$smFieldName"
                    } else if (!omitEntityName) {
                        // Вложенная сущность - не массив
                        smFieldName = getInputSmFieldContext(smFieldName, pathPrefix, entityName)
                    }
                    setSmValFromResultSet(smLayout, smFieldName, rs, mapEntry.get("DB_FIELD"), mapEntry.get("FIELD_TYPE"), mapEntry.get("FIELD_DATA_TYPE"))
                }
        }
    }

    static void setSmValFromResultSet(IHData smLayout, String smFieldName, ResultSet rs, String dbFieldName, String dataType, String fieldType) {
        Object val = null
        try {
            switch (dataType) {
                case "DATE":
                    val = rs.getDate(dbFieldName)
                    break
                case "OBJECT":
                    val = rs.getObject(dbFieldName)
                    break
                case "GROOVY_SPR_EXT":
                    switch (rs.getString(dbFieldName)) {
                        case "1": val = "BCS"; break
                        case "2": val = "CRE_CH"; break
                        case "3": val = "PIM"; break
                        case "4": val = "AGR"; break
                        case "5": val = "CRE_SC"; break
                        case null: val = null; break
                        default: throw new Exception(
                                "SPR_CD value not found in dictionary [$dataType] for RKK_CD (field $dbFieldName) with value [${rs.getString(dbFieldName)}]")
                    }
                    break
                default:
                    val = decodeRccToSpr(dataType, rs.getObject(dbFieldName) as Integer)
            }
            log.debug("Field data type is {}", fieldType)
            if (fieldType == "boolean" && val != null) {
                if (val) {
                    val = 1;
                }else {
                    val = 0;
                }
                log.debug("Patch: boolean value converted to {}", val);
            }
            dataDepth++
            smLayout.setValue(smFieldName, val)
            String smVal = val.toString()
            log.debug("Passed DB value '$dbFieldName' ==> SM value '${smLayout.getLayout()}.$smFieldName' as $dataType: '$val'")
        } catch (Exception e) {
            log.error("Failed to pass DB value '$dbFieldName' ==> SM value '${smLayout.getLayout()}.$smFieldName' as $dataType: $e", e)
            throw e
        }
    }

    static String getInputSmFieldContext(String smFieldName, String pathPrefix, String entityName) {
        return (smFieldName.startsWith(SM_PATH_ROOT_PATTERN) ?
                smFieldName.replace(SM_PATH_ROOT_PATTERN, "") :   // Если нужно взять элемент из корневого LDS
                "$pathPrefix$entityName.$smFieldName")                          // Если из текущего контекста
    }

    static void mapDbRecordToSm(ResultSet rs, Map<String, IHData> smLayoutsMap, RccEntity rccEntity, Integer index) {
        mapDbRecordToSm(rs, smLayoutsMap, rccEntity, index, "", true)
    }

    static Object getSmVal(IHData data, String name) {
        Object retVal = data.getValue(name)
        log.debug("Get '${data.getLayout()}.$name' SM characteristic value: '$retVal'")
        return retVal
    }

    static void setSmVal(IHData data, String name, Object val) {
        log.debug("Set '${data.getLayout()}.$name' SM characteristic value: '$val'")
        dataDepth++
        data.setValue(name, val)
    }

    static void setSmValfromJson(IHData data, String name, Object val) {
        dataDepth++
        String classname = null
        val = checkValForDate(val)
        if ((name.contains("DT") || name.contains("DTTM") || name.contains("DATE")) && (val == "" || val == null)) val = null as Date
        if (val != null) classname = val.getClass().simpleName
        if (classname == "Double" && val != null) val = val as Double
        log.debug("Set '${data.getLayout()}.$name' SM characteristic from Json, value: '$val'")
        data.setValue(name, val)
    }

    static Object checkValForDate(Object val) {
        DateFormat df = new SimpleDateFormat(JSON_DATE_FORMAT)
        DateFormat sm = new SimpleDateFormat(SM_DATE_FORMAT)
        Date date
        try {
            date = df.parse(val.toString())
            //date = sm.parse(val.toString())
            //date = Date.parse(SM_DATE_FORMAT, val.toString())
            val = date
            log.info('changed to date')
            return val
        } catch (ParseException e) {
            log.info('exception in changed to date')
            return val
        }
    }

    static void setSmSizeVal(IHData data, String name, int val){
        log.debug("${data.getLayout()}.setSize('$name', $val)")
        data.setSize(name, val)
    }

    static IHData createSmControlBlock(String alias, String signature) {
        IHData controlData = new HierarchicalDatasource("OCONTROL") as IHData
        setSmVal(controlData, "ALIAS", alias)
        setSmVal(controlData, "SIGNATURE", signature)
        return controlData
    }

    static IHData addHierarchicalDatasource(String name, Map<String, IHData> smLayoutsMapMap) {
        IHData smLayout = new HierarchicalDatasource(name) as IHData
        smLayoutsMapMap.put(name, smLayout)
        return smLayout
    }

    static void locateFirstRecordRecursively(ConcurrentHashMap<String, ResultSet> rsMap, RccEntity rccEntity) {
        log.debug("Locating first [${rccEntity.entityName}] record...")
        try {
            ResultSet resultSet = rsMap.get(rccEntity.entityName)
            if (resultSet == null) throw new SmCallException("ResultSet not found in rsMap")
            resultSet.first()
            globalConfSqlTreeInput.get(rccEntity.entityName)?.each {
                childEntity, childRccEntity ->
                    locateFirstRecordRecursively(rsMap, childRccEntity)
            }
        } catch (Exception e) {
            log.error("Exception handled while locating first record for keyless entity [${rccEntity.entityName}]: $e", e)
            throw e
        }
    }

    static String getSmArrayCounterName(String entityName) {
        return entityName + "_COUNT"
    }

    static Exception mapAllDbEntitiesToSm(Long syncIdParentRec, Long customerId, Long packId, Long packGroupBy, Map<String, IHData> smLayoutsMap, RccEntity rccEntity,
                                          ConcurrentHashMap<String, ResultSet> rsMap, String pathPrefix, String stageCd) {
        String entityName = rccEntity.entityName
        IHData smLayout = smLayoutsMap.get(rccEntity.smLayoutName)
        log.info("Processing [$entityName]...")

        ResultSet resultSet = rsMap.get(entityName)
        if (resultSet == null) throw new SmCallException("ResultSet for entity [$entityName] not found in rsMap")

        boolean lastRecordFetched = false
        int i = 0
        Exception recoverableExceptionHandled = null

        // Для сущностей без связи - переход на первую строку, чтобы передать все строки
        if (rccEntity.syncResultsetCol == null) {
            locateFirstRecordRecursively(rsMap, rccEntity)
        }

        while (!lastRecordFetched) {
            try {
                if (resultSet.getRow() == 0) { // Первый проход
                    log.debug("Fetching first [$entityName] record...")
                    if (!resultSet.next()) {
                        //log.warn("No records of [$entityName] entity found for Customer (ID=$syncIdParentRec)")
                        break
                    }
                    log.debug("First [$entityName] record fetched!")
                }

                //TODO сделать проход по данным до данных с нужным customerID если такой имеется
                Long syncIdCurrentRec = null
                if (rccEntity.syncResultsetCol != null) {
                    // Поле для синхронизации курсоров валидно только для сущностей, имеющих связь
                    log.debug("Before linkage to parent: syncIdParentRec = $syncIdParentRec, syncIdCurrentRec = $syncIdCurrentRec")
                    syncIdCurrentRec = resultSet.getObject(rccEntity.syncResultsetCol) as Long
                    long rsNext = 0
                    while (syncIdCurrentRec != syncIdParentRec && resultSet.next()) {
                        log.debug("Checking [$entityName] linkage to parent: syncIdParentRec = $syncIdParentRec, syncIdCurrentRec = $syncIdCurrentRec")
                        syncIdCurrentRec = resultSet.getObject(rccEntity.syncResultsetCol) as Long
                        //if (resultSet.next()) resultSet.next() else rsNext = 1
                    }
                }

                // Проверка, что есть связь по ключу (FK) или связь не требуется
                if (syncIdCurrentRec == syncIdParentRec || rccEntity.syncResultsetCol == null) {
                    log.info("IN syncIdCurrentRec == syncIdParentRec || rccEntity.syncResultsetCol == null")
                    i++
                    Integer index = null
                    if (rccEntity.arrayType in [ENTITY_ARRAY_TYPE_DYNAMIC, ENTITY_ARRAY_TYPE_STATIC]) {
                        index = i
                        log.debug("$entityName is treated as array. Index value to use: $index")
                    }
                    try {
                        log.info("Next [$entityName] linked record found! Passing value to SM...")
                        if (rccEntity.arrayType == ENTITY_ARRAY_TYPE_DYNAMIC) {
                            log.debug("${smLayout.getLayout()}.setSize('$pathPrefix$entityName', $i)")
                            smLayout.setSize("$pathPrefix$entityName", i)
                        }
                        mapDbRecordToSm(resultSet, smLayoutsMap, rccEntity, index, pathPrefix, false)
                    } catch (Exception e) {
                        /*********************************************************************************************************
                         * Нам необходимо проскроллить все ResultSet`ы до конца текущего Customer`а
                         * Поэтому нельзя просто выбрасывать исключение и прерывать цикл
                         *********************************************************************************************************/
                        recoverableExceptionHandled = e
                        //logEventError("Processing of Customer failed. Exception: $recoverableExceptionHandled, ${getHeapMemInfoAndGST(e)}", EVENT_PROCESS_BATCH_CUST, ERR_PROCESS_BATCH_CUST, packId, customerId, log, recoverableExceptionHandled)
                        log.error("Processing of Customer failed. Exception: $recoverableExceptionHandled")
                    }
                    /*******************************************************************************************************
                     * Рекурсия на дочерние сущности
                     *******************************************************************************************************/
                    globalConfSqlTreeInput.get(entityName)?.each {
                        childEntity, childRccEntity ->
                            if (checkEntityIsActive(childRccEntity, packGroupBy, stageCd)) {
                                /*******************************************************************************************************
                                 * Выполняем маппинг в SM для этой сущности, если она активна
                                 *******************************************************************************************************/
                                log.info("Entity [$childEntity] identified as active - proceed to SM mapping...")

                                /*******************************************************************************************************
                                 * Получаем значение поля-синхронизатора для дочерней сущности
                                 *******************************************************************************************************/
                                Long syncIdChildRec = null
                                try {
                                    syncIdChildRec = resultSet.getObject(childRccEntity.syncResultsetCol) as Long
                                } catch (Exception e) {
                                    throw new Exception("Failed to get child sync column [${childRccEntity.syncResultsetCol}] from [$entityName] Resultset! Error: ${e.getMessage()}")
                                }

                                String childPathPrefix = (index == null ? "$pathPrefix$entityName." : "$pathPrefix$entityName[$index].")
                                Exception childException = mapAllDbEntitiesToSm(syncIdChildRec, customerId, packId, packGroupBy, smLayoutsMap, childRccEntity, rsMap, childPathPrefix, stageCd)
                                if (childException != null) recoverableExceptionHandled = childException
                            } else {
                                log.info("Entity [$childEntity] identified as inactive - skipping SM mapping")
                            }

                    }

                    lastRecordFetched = !resultSet.next() //лагает тут, переходит в цикл
                    log.info("lastRecordFetched = ${lastRecordFetched.toString()}")
                    //resultSet.next()

                } else { // Нет связи по ключу, значит чужая запись
                    log.debug("Orphan [$entityName] record found - skip...")
                    lastRecordFetched = true  // Выходим из цикла
                }
            } catch (Exception e) {
                //recoverableExceptionHandled = e
                log.error("Failed to process [$entityName] record: ${e.getMessage()}", e)
                throw e
            }
        }
        log.info("Processing [$entityName] completed for Customer (ID=$syncIdParentRec)")
        setSmVal(smLayout, pathPrefix + getSmArrayCounterName(entityName), i)
        return recoverableExceptionHandled
    }

    static Exception mapAllDbEntitiesToSm(Long customerId, Long packId, Long packGroupBy, Map<String, IHData> smLayoutsMap, RccEntity rccEntity,
                                          ConcurrentHashMap<String, ResultSet> rsMap, String stageCd) {
        return mapAllDbEntitiesToSm(customerId, customerId, packId, packGroupBy, smLayoutsMap, rccEntity, rsMap, "", stageCd)
    }

    /**************************************************************************************
     *  Перевод массива всех layouts SM в HashMap и извлечение layouts с результатами
     **************************************************************************************/
    static HashMap<String, IHData> smLayoutArrayToMap(IHData[] smDataArr) {
        HashMap<String, IHData> smDataMap = new HashMap<String, IHData>(smDataArr.size())
        log.info("Setting layouts from smDataArr")
        for (IHData layout : smDataArr) {
            smDataMap.put(layout.getLayout(), layout)
            log.info("smDataMap.put ${layout.getLayout()}")
        }
        return smDataMap
    }

    static String getInputSmFieldWithContext(String smFieldName, String pathPrefix) {
        return (smFieldName.startsWith(SM_PATH_ROOT_PATTERN) ?
                smFieldName.replace(SM_PATH_ROOT_PATTERN, "") :     // Если нужно взять элемент из корневого LDS
                pathPrefix + smFieldName)                                       // Если из текущего контекста
    }
}

@CompileStatic
class DecisionAgentTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(this)
    private static final String recordTracePrefix = "CustId:"

    private IHData[] executionData
    private int traceFlags
    private String recordTraceId
    private Long customerId
    private Long packId
    private Exception exceptionHandled
    private boolean usePoisonPill;

    DecisionAgentTask(IHData[] executionData, int traceFlags, Long customerId, Long packId, Exception exceptionHandled, boolean usePoisonPill) {
        this.executionData = executionData
        this.traceFlags = traceFlags
        //recordTraceId = recordTracePrefix + customerId.toString()
        this.customerId = customerId
        this.packId = packId
        this.exceptionHandled = exceptionHandled
        this.usePoisonPill = usePoisonPill
    }

    void run() {
        try {
            if (customerId != null) Thread.currentThread().setName("CustId:$customerId")

            if (exceptionHandled != null) throw new SmCallException(exceptionHandled.getMessage())

            Instant start = Instant.now()
            log.info("Executing DA call...")
            int daReturnCode = -1
            try {
                log.info("TraceFlags = $traceFlags")
                log.info("recordTraceId = $recordTraceId")
                daReturnCode = BatchJSEMObjectInterface.instance().execute(executionData, traceFlags, recordTraceId)
                //package com.experian.eda.framework.runtime.common.exceptions и trace; содержится ошибка
            } catch (Exception e) {
                throw new SmCallException(e.getMessage())
            }
            log.info("DA call executed")

            // Проверка ошибок вызова
            checkDaErrors(executionData, daReturnCode)
            log.info("Saving result data...")
            SaveResultData(executionData, rccDbCommitAfter, usePoisonPill)
            successfulRecords.incrementAndGet()

            log.info("SM Call completed")
        } catch (SmPackProcessException e) {
            stopSemaphore.set(true)
            failedRecords.set(totalRecords.get())
            successfulRecords.set(0)
            log.error("Batch processing of whole Pack failed. Exception: $e")
        } catch (SmCallException e) {
            failedRecords.incrementAndGet()
            log.error("Failed execute strategy for Customer in Pack: $e")
            SaveResultError(executionData, e.getMessage(), rccDbCommitAfter, usePoisonPill)
        } catch (Exception e) {
            failedRecords.incrementAndGet()
            log.error("Failed to process Customer in Pack: $e")
        } finally {
            if (traceFlags > 0) SaveDALog(customerId)
            executionData = null // данные текущего клиента больше не нужны...
        }
    }

    static checkDaErrors(IHData[] executionData, int daReturnCode) {
        // Проверка ошибок вызова
        IHData controlData = executionData[0] as IHData
        String errorStr = (String) controlData.getValue("ERRORCOUNT")
        log.info("DA call error count: $errorStr")
        int errorCount = Integer.parseInt(errorStr)
        if (errorCount > 0 || daReturnCode != 0) {
            String errorCode, errorList = ""
            for (int errIndex = 1; errIndex <= errorCount; errIndex++) {
                errorCode = controlData.getValue("ERROR[$errIndex]")
                log.error("DA error: $errorCode")
                errorList += "$errorCode;"
            }
            throw new SmCallException("DA call errors: [$errorList], check 'da.log' file for details")
        }

        // Получаем SM layout`ы с результатами вызова
        HashMap<String, IHData> smDataMap = smLayoutArrayToMap(executionData)
        IHData smDecisionData = smDataMap.get(SM_LAYOUT_DECISION)
        IHData smStateData = smDataMap.get(SM_LAYOUT_STATE)

        // Получаем код и текст бизнес-ошибки
        Integer errCode = getSmVal(smStateData, SM_FIELD_ERROR_CODE) as Integer
        String errMsg = getSmVal(smStateData, SM_FIELD_ERROR_MSG) as String

        if (errCode != null && errCode != 0) {
            throw new SmCallException("SM business error: $errMsg")
        }

    }
}

@CompileStatic
class ExtBatchSaveResultData {
    private static final Logger log = LoggerFactory.getLogger(this)
    private static AtomicInteger saveCounter = new AtomicInteger(0)

    public static Connection rccConn = null
    public static Connection pcsmConn = null
    public static Map<String, NamedPreparedStatement> outputPsMap = null

    static String convertWithIteration(HashMap<String, ?> map) {
        StringBuilder mapAsString = new StringBuilder("{");
        for (String key : map.keySet()) {
            mapAsString.append(key + "=" + map.get(key) + ", ");
        }
        mapAsString.delete(mapAsString.length()-2, mapAsString.length()).append("}");
        return mapAsString.toString();
    }

    synchronized static void SaveDALog(Long customerId) {
        try{
            def newFile = new File("output/log_${customerId}.log")
            new File('logs\\da.log').text.tokenize( '\n' ).findAll {
                it.contains "${customerId}"
            }.each {
                newFile << "$it"
            }
        }
        catch(Exception e) {
            throw new Exception("SPR da log error: " + e.getMessage())
        }
    }

    static void CreateCsvFile(){
        String[] csvColumns = new File('input\\csvColumns.txt') as String[]

        csvColumns.each { input ->
            HashMap<String, String> map = new HashMap<String, String>()
            def str = input.split(/\,/).collect{it as String}
            map.put("layout", str[0])
            map.put("object", str[1])
            csvColumnsList.add(map)
        }

        Workbook wb = new HSSFWorkbook()
        Sheet sheet = wb.createSheet("Table")
        Row header = sheet.createRow(0)
        Integer cellid = 0
        for (HashMap<String, String> map : csvColumnsList) {
            Cell cell = header.createCell(cellid++)
            String name = map.get("layout") + '.' + map.get("object")
            cell.setCellValue(name)
        }

        FileOutputStream out = new FileOutputStream(new File('output\\csvTable.xls'))
        wb.write(out)
        out.close()
    }

    synchronized static void SaveResultData(IHData[] smDataArr, int batchSize, boolean usePoisonPill) {
        log.info("SaveResultData #${saveCounter + 1} started")

        // Получаем SM layout`ы с результатами вызова
        HashMap<String, IHData> smDataMap = smLayoutArrayToMap(smDataArr)
        IHData smStateData = smDataMap.get(SM_LAYOUT_STATE)
        IHData smDecisionData = smDataMap.get(SM_LAYOUT_DECISION)
        log.info("Successfully received SM result IHData objects")

        Long customerIdd= getSmVal(smDecisionData, 'CUSTOMER_ID') as Long

        // Считаем значение PackGroupBy (битовые флаги)
        log.info(".Calculating packGroupBy value..")
        /*if (getTenantProperty("mapping.optimization.disable", false) != "true") {
            setSmVal(smStateData, SM_FIELD_PACK_GROUP_BY, calcPackGroupBy(smStateData))
        } else {
            setSmVal(smStateData, SM_FIELD_PACK_GROUP_BY, PACK_GROUP_BY_DEF_VAL)
        }*/

        //TODO убрал так как не надо особо
        //setSmVal(smStateData, SM_FIELD_PACK_GROUP_BY, calcPackGroupBy(smStateData))

        //Формирование JSON Файла
        log.info("Building SPR JSON response...")
        JsonObject responseRoot = mapSmToJson(smDataArr)

        def aaa123 = smDataMap.get('DECISION_RESULT').getValue('PTI[1].LIABILITY_MAX_PAY_AMT')

        String jsonResponse = jsonObjectToString(responseRoot)
        def newFile = new File("output/output_${customerIdd}.json")
        newFile.write(jsonResponse)
        log.info("Successfully built JSON response")

        Integer cnt = saveCounter.toInteger() + 1
        if (csvOutput == true) {
            POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream('output\\csvTable.xls'))
            Workbook wb = new HSSFWorkbook(fs)
            Sheet sheet = wb.getSheet('Table')
            Row header = sheet.createRow(cnt)
            Integer cellid = 0
            for (HashMap<String, String> map : csvColumnsList) {
                Cell cell = header.createCell(cellid++)
                String val = smDataMap.get(map.get("layout")).getValue(map.get("object")).toString()
                //String name = map.get("layout") + '.' + map.get("object")
                cell.setCellValue(val)
            }

            FileOutputStream out = new FileOutputStream(new File('output\\csvTable.xls'))
            wb.write(out)
            out.close()

        }

        saveCounter.incrementAndGet()

        /*File csvTable = new File('output\\csvTable.csv')
        csvColumns.each{input->

            log.info("csv = $input")
            def str = input.split(/\./).collect{it as String}
            str.each{obj->
                String objName = obj
                Integer objIndex = 0
                if (obj.contains('[')) {
                    objName = obj.substring(0,obj.indexOf('['))
                    objIndex = obj.substring(obj.indexOf('[') + 1, obj.indexOf(']') ) as Integer
                }
                log.info("csvName = $objName csvIndex = $objIndex")
                if (jsonObj["$objName"].getClass().simpleName == 'JsonObject') {
                    jsonObj = jsonObj["$objName"]
                } else {
                    jsonObj = jsonObj["$objName"].collect()[objIndex]
                }
            }
            def finalval = jsonObj
            csvRow << "$finalval,"

        }
        csvTable.withWriter {fileWriter ->
            def csvFilePrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT)
            csvFilePrinter.printRecord(csvRow)
        }
        */

    }

    static String jsonObjectToString(JsonElement root) {
        Gson gson = gsonBuilder.create()
        String jsonString = gson.toJson(root);
        log.debug("JSON as string:\n$jsonString")
        return jsonString
    }

    static Long calcPackGroupBy(IHData smStateData) {
        HashMap<String, Boolean> allActiveEntities = new HashMap<String, Boolean>()
        int i = 0
        log.debug("calcPackGroupBy: retrieve all $SM_FIELD_NEXT_SM_INPUT_ENTITY_CD[] elements...")
        while (true) { // Получим список всех активных сущностей в HashMap для удобства поиска
            String nextEntity = getSmVal(smStateData, "$SM_FIELD_NEXT_SM_INPUT_ENTITY_CD[${++i}]")
            if (nextEntity?.trim()) { // Если не null и не пустая строка
                if (nextEntity == "CUSTOMER") continue // CUSTOMER есть всегда, т.к. корень дерева

                // Проверим, что активная сущность есть в дереве сущностей
                if (globalConfSqlFlatMapInput.get(nextEntity) == null) throw new Exception("Failed to parse SM result: " +
                        "$SM_FIELD_NEXT_SM_INPUT_ENTITY_CD[${i}] = '$nextEntity' is not found in $SM_CONF_BATCH_SQL_TABLE_NAME table")

                allActiveEntities.put(nextEntity, true)
            } else break
        }
        log.debug("calcPackGroupBy: allActiveEntities = $allActiveEntities")

        Long packGroupBy = 0B1 // Считаем, что Customer есть всегда (1 - это флаг Customer`а):
        packGroupBy = calcPackGroupByTree(allActiveEntities, SQL_ENTITY_CUSTOMER, packGroupBy)
        log.debug("calcPackGroupBy: final binary value = ${Long.toBinaryString(packGroupBy)}")
        log.debug("calcPackGroupBy: final decimal value = $packGroupBy")
        return packGroupBy
    }

    private static Long calcPackGroupByTree(HashMap<String, Boolean> allActiveEntities, String parentEntityName, Long currentPackGroupBy) {
        globalConfSqlTreeInput.get(parentEntityName)?.each {
            childEntityName, rccEntity ->
                log.debug("calcPackGroupBy: iterating $childEntityName element...")
                boolean entityFound = allActiveEntities.get(childEntityName)
                log.debug("calcPackGroupBy: entity $childEntityName bind state is: $entityFound")
                if (entityFound) {
                    log.debug("${Long.toBinaryString(currentPackGroupBy)} | ${Long.toBinaryString(rccEntity.bindBit)} = " +
                            "${Long.toBinaryString(currentPackGroupBy | rccEntity.bindBit)}")
                    currentPackGroupBy = (currentPackGroupBy | rccEntity.bindBit) as Long
                    Long childPackGroupBy = calcPackGroupByTree(allActiveEntities, childEntityName, currentPackGroupBy)
                    if (childPackGroupBy != null) currentPackGroupBy = childPackGroupBy
                }
        }
        return currentPackGroupBy
    }

    synchronized static void SaveResultError(IHData[] smDataArr, String errorMsg, int batchSize, boolean usePoisonPill) {
        log.info("SaveResultError #${saveCounter + 1} started")

        // Получаем SM layout`ы с результатами вызова
        HashMap<String, IHData> smDataMap = smLayoutArrayToMap(smDataArr)
        IHData smDecisionData = smDataMap.get(SM_LAYOUT_DECISION)
        IHData smStateData = smDataMap.get(SM_LAYOUT_STATE)
        log.info("Successfully received SM result IHData objects")

        // Заполняем код и текст ошибки для DECISION_RESULT
        setSmVal(smDecisionData, SM_FIELD_SPR_PROCESSING_STATUS, SPR_PROCESSING_STATUS_ERROR_NO_REPROCESS)

        // Заполняем код и текст ошибки для SPR_RESULT
        Integer errCode = getSmVal(smStateData, SM_FIELD_ERROR_CODE) as Integer

        if (errCode == null || errCode == 0) {
            setSmVal(smStateData, SM_FIELD_ERROR_CODE, SM_PROC_ERROR_CODE_DEF)
        }
        //setSmVal(smStateData, SM_FIELD_ERROR_MSG, StringUtils.left(errorMsg, SPR_RESULT_ERROR_MSG_MAX_LENGTH))

    }
}

@CompileStatic
class JsonProcessingHelpers {

    private static final Logger log = LoggerFactory.getLogger(this)
    public static final String JSON_DATETIME_MS_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    public static final String JSON_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    public static final String JSON_DATETIME_Z_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX"
    public static final String JSON_DATE_FORMAT = "yyyy-MM-dd"
    public static final String SM_DATE_FORMAT = "yyyyMMdd"

    static JsonObject mapSmTreeToJson(RccEntity rccEntity, HashMap<String, IHData> smLayoutsMap, String jsonContextPath, String smContextPath, AtomicInteger nonEmptyAttributesCount, HashMap<String, HashMap<String, RccEntity>> globalConfSqlTree, HashMap<String, ArrayList<HashMap<String, String>>> globalConfMap) {
        if (rccEntity == null) throw new Exception("Null entity found for JSON object under path [$jsonContextPath]. Please revise entity tree in SM_CONF_ENTITY_TREE table")

        log.info("Processing all children of entity ${rccEntity.entityName}...")
        log.info("JSON context = $jsonContextPath")
        log.info("SM context = $smContextPath")

        /*******************************************************************************************
         * Создание JSON-объекта текущего узла и маппинг аго атрибутов (примитивов)
         *******************************************************************************************/
        JsonObject currentJsonObject = new JsonObject();
        nonEmptyAttributesCount.set(mapSmAttributesToJson(rccEntity, currentJsonObject, smLayoutsMap, jsonContextPath, smContextPath, globalConfMap))

        globalConfSqlTree.get(rccEntity.entityName)?.each {
            childEntityId, childRccEntity ->
                /*******************************************************************************************
                 * Цикл по всем дочерним сущностям, описанных в модели SM_CONF_ENTITY_TREE
                 *******************************************************************************************/
                log.debug("Found next output entity $childEntityId in logical tree model: jsonObjectName = ${childRccEntity.entityName}, " +
                        "jsonObjectType = ${childRccEntity.arrayType}")
                AtomicInteger childObjNonEmptyAttributesCount = new AtomicInteger(0)
                if (childRccEntity.arrayType == null) {
                    JsonObject childJsonElement = mapSmTreeToJson(childRccEntity, smLayoutsMap,
                            "$jsonContextPath${childRccEntity.entityName}.", "$smContextPath${childRccEntity.entityName}.", childObjNonEmptyAttributesCount, globalConfSqlTree, globalConfMap)
                    currentJsonObject.add(childRccEntity.entityName, childJsonElement)
                } else {
                    log.info("smLayoutsMap.get(childRccEntity.smLayoutName) = ${smLayoutsMap.get(childRccEntity.smLayoutName)}")
                    log.info("entityName = $smContextPath${childRccEntity.entityName}")
                    Integer arrCount = getSmVal(smLayoutsMap.get(childRccEntity.smLayoutName),
                            getSmArrayCounterName("$smContextPath${childRccEntity.entityName}")) as Integer
                    log.info("arrCount = $arrCount")
                    if (arrCount > 0) {
                        JsonArray childJsonArray = new JsonArray();
                        /*******************************************************************************************
                         * Цикл по всем элементам массива
                         *******************************************************************************************/
                        for (int arrIndex = 1; arrIndex <= arrCount; arrIndex++) {
                            log.debug("Processing array element: $jsonContextPath${childRccEntity.smLayoutName}[$arrIndex]")
                            JsonObject childJsonElement = mapSmTreeToJson(childRccEntity, smLayoutsMap,
                                    "$jsonContextPath${childRccEntity.entityName}[$arrIndex].",
                                    "$jsonContextPath${childRccEntity.entityName}[$arrIndex].",
                                    childObjNonEmptyAttributesCount, globalConfSqlTree, globalConfMap)
                            childJsonArray.add(childJsonElement)
                        }
                        currentJsonObject.add(childRccEntity.entityName, childJsonArray)
                    }
                }
        }
        return currentJsonObject
    }

    static int mapSmAttributesToJson(RccEntity rccEntity, JsonObject currentJsonObject, Map<String, IHData> smLayoutsMap, String jsonContextPath, String smContextPath, HashMap<String, ArrayList<HashMap<String, String>>> globalConfMap) {
        int nonEmptyAttributesCount = 0
        if (rccEntity == null) throw new Exception("Null entity found for JSON object under path [$jsonContextPath]. Please revise entity tree in SM_CONF_ENTITY_TREE table")

        IHData smLayout = smLayoutsMap.get(rccEntity.smLayoutName)
        String entityId = rccEntity.entityName
        log.info("entityId = $entityId")

        ArrayList<HashMap<String, String>> mapConf = globalConfMap.get(rccEntity.entityName)
        if (mapConf == null || mapConf?.size() < 1) {
            log.warn("No SM mapping found in $SM_CONF_BATCH_MAP_TABLE_NAME table for entity [$entityId] - SKIP")
            return nonEmptyAttributesCount
        }

        for (Map<String, String> mapEntry : globalConfMap.get(entityId)) {
            String smFieldName = getInputSmFieldWithContext(mapEntry.get("SM_FIELD"), smContextPath)
            String jsonFieldName = mapEntry.get("DB_FIELD")
            String fieldType = mapEntry.get("FIELD_TYPE")
            String smFieldNameFull = jsonFieldName
            //if (smLayout.getLayout()+"." != jsonContextPath) smFieldNameFull = jsonContextPath + jsonFieldName
            log.info("smFieldNameFull = $smFieldNameFull smLayout.getLayout() = ${smLayout.getLayout()} ")
            log.debug("Processing map entry: SM[$smFieldName] ==> JSON[$jsonContextPath$jsonFieldName]")
            log.debug("smFieldName = $smFieldName] ==> JSON jsonContextPath=$jsonContextPath jsonFieldName=$jsonFieldName")
            Object smVal = null
            Object finalVal = null
            try {
                smVal = getSmVal(smLayout, smFieldNameFull)
                String val = smVal.toString()
                currentJsonObject.addProperty(jsonFieldName, val)
                finalVal = val
                if (finalVal != null) nonEmptyAttributesCount++
                log.info("Passed SM field '${smLayout.getLayout()}:$smFieldName' ==> JSON field '$jsonContextPath$jsonFieldName' as $fieldType: '$finalVal'")
            } catch (Exception e) {
                throw new SprResponseBuildException("Error reading SM result field '${rccEntity.smLayoutName}->$smFieldName': $e")
            }
            log.info("Get SM field '$smFieldName' value = $smVal")
            //if (smVal == null) continue
            /*Object finalVal = null
            try {
                try {
                    switch (fieldType) {
                        case "DATE":
                            String val = dateToXmlDateStr(smVal as Date)
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        case "DATETIME":
                            String val = dateToXmlDateTimeStr(smVal as Date)
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        case "OBJECT":
                            if (smVal instanceof Number) {
                                Number val = smVal as Number
                                currentJsonObject.addProperty(jsonFieldName, val);
                                finalVal = val
                            } else {
                                String val = smVal?.toString()
                                currentJsonObject.addProperty(jsonFieldName, val);
                                finalVal = val
                            }
                            break
                        case "NUM":
                            Number val = smVal as Number
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        case "BOOL":
                            Object objVal = smVal
                            Boolean val
                            switch (objVal) {
                                case 1: val = true; break
                                case 0: val = false; break
                                case '1': val = true; break
                                case '0': val = false; break
                                case null: val = null; break;
                                default: throw new Exception("Incorrect flag value [$objVal]")
                            }
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        case "SPR_EXT":
                            String strVal = smVal as String
                            Integer val
                            switch (strVal) {
                                case "BCS": val = 1; break
                                case "CRE_CH": val = 2; break
                                case "PIM": val = 3; break
                                case "AGR": val = 4; break
                                case "CRE_SC": val = 5; break
                                case "DWH": val = 6; break
                                case null: val = null; break
                                default: throw new Exception("Incorrect SPR_EXT value [$strVal]")
                            }
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        case "NVLSTR":
                            String val = smVal?.toString()
                            if (val?.trim() == "") val = null
                            currentJsonObject.addProperty(jsonFieldName, val);
                            finalVal = val
                            break
                        default:
                            Object val1
                            log.info("smVal = $smVal; smFielName = $smFieldName; jsonFiledName = $jsonFieldName")
                            if (smVal instanceof Number) {
                                val1 = smVal as Number
                                log.info("Зашел в decodeRccToSpr")
                                finalVal = decodeRccToSpr(val1 as String, smFieldName as Integer)
                                log.info("Вышел из decodeRccToSpr")
                            }
                            if (smVal == null) continue
                            if (smVal== null) {
                                log.info("Зашел в нуль")
                                finalVal = decodeRccToSpr(smVal as String, smFieldName as Integer)
                                log.info("Вышел из decodeRccToSpr")
                            }
                            else {
                                val1 = smVal?.toString()
                                log.info("Зашел в decodeRccToSpr")
                                finalVal = decodeRccToSpr(val1, smFieldName as Integer)
                                log.info("Вышел из decodeRccToSpr")
                            }
                            break
                    }
                    }
                    catch (Exception e) {
                        throw new Exception("Unexpected ${SM_CONF_BATCH_MAP_TABLE_NAME}.FIELD_TYPE=$fieldType received for SM field $smFieldName")
                    }
                String val = smVal.toString()
                currentJsonObject.addProperty(jsonFieldName, val);
                finalVal = val
                if (finalVal != null) nonEmptyAttributesCount++
                log.info("Passed SM field '${smLayout.getLayout()}:$smFieldName' ==> JSON field '$jsonContextPath$jsonFieldName' as $fieldType: '$finalVal'")
            } catch (Exception e) {
                throw new SprResponseBuildException("Error adding response JSON value '$jsonContextPath$jsonFieldName' as $fieldType: $e")
            }*/
        }
        return nonEmptyAttributesCount
    }

    static String mapJsonTreeToSm(JsonElement jsonElement, RccEntity rccEntity, Map<String, IHData> smLayoutsMap, String jsonContextPath, String smContextPath) {
        //Проверка на array или нет (MAP-одиночное поле) ARRAY - список. Список можно вычислить по SM_FIELD contains ('%[i]%')
        if ((rccEntity == globalConfSqlFlatMapInput.get(SQL_TREE_INPUT_ROOT)) && (rccEntity.arrayType == "MAP")) {
            log.info("Processing all direct attributes of root entity ${rccEntity.entityName}...")
            mapJsonAttributesToSm(jsonElement, rccEntity, smLayoutsMap,
                    "", "")
        }

        log.info("Processing all children of entity ${rccEntity.entityName}...")
        log.info("JSON context = $jsonContextPath")
        log.info("SM context = $smContextPath")

        if (jsonElement.isJsonObject()) {
            /*******************************************************************************************
             * Если текущий jsonElement - не JsonObject, то у него нет дочерних элементов,
             * что является терминальным случаем обхода дерева.
             * Массивы и примитивы обрабатываются как дочерние элементы JsonObject уровнем выше
             *******************************************************************************************/
            log.info("Entity ${rccEntity.entityName} is JsonObject. Iterating children...")

            globalConfSqlTreeInput.get(rccEntity.entityName)?.each {
                    /*******************************************************************************************
                     * Цикл по всем дочерним сущностям, описанных в модели SM_CONF_ENTITY_TREE
                     *******************************************************************************************/
                childEntityId, childRccEntity ->
                    // Если текущей сущности нет в мапинге для заданного вызова - выходим
                    log.debug("Found next entity $childEntityId in logical tree model: jsonObjectName = ${childRccEntity.entityName}, jsonObjectType = ${childRccEntity.arrayType}")

                    /*******************************************************************************************
                     * Получаем дочерний JsonElement по имени и проверяем его тип
                     *******************************************************************************************/
                    JsonElement childJsonElement = jsonElement.asJsonObject.get(childRccEntity.entityName)
                    if (childJsonElement != null) {
                        if (childRccEntity.arrayType == null) {
                            if (!childJsonElement.isJsonObject())
                                throw new Exception("Unexpected type of JSON element defined as [${childRccEntity.arrayType}] found in message for entity $childEntityId. " +
                                        "Expected type: [JsonObject], actual type: [${childJsonElement?.getClass()?.getName()}]")

                            /*******************************************************************************************
                             * Выполняем маппинг всех вложенных примитивов
                             *******************************************************************************************/
                            mapJsonAttributesToSm(childJsonElement, childRccEntity, smLayoutsMap,
                                    "$jsonContextPath${childRccEntity.entityName}.", "$smContextPath${childRccEntity.smLayoutName}.")

                            /*******************************************************************************************
                             * Если в описании модели SM_CONF_ENTITY_TREE у текущей сущности есть дочерние сущности,
                             * то рекурсивно обрабатываем их
                             *******************************************************************************************/
                            if (globalConfSqlTreeInput.get(childRccEntity.entityName)?.size() > 0)
                                mapJsonTreeToSm(childJsonElement, childRccEntity, smLayoutsMap,
                                        "$jsonContextPath${childRccEntity.entityName}.", "$smContextPath${childRccEntity.smLayoutName}.")
                        } else {
                            if (!childJsonElement.isJsonArray())
                                throw new Exception("Unexpected type of JSON element defined as [${childRccEntity.arrayType}] found in message for entity $childEntityId. " +
                                        "Expected type: [JsonArray], actual type: [${childJsonElement?.getClass()?.getName()}]")
                            int arrIndex = 0
                            /*******************************************************************************************
                             * Цикл по всем элементам массива
                             *******************************************************************************************/
                            for (JsonElement arrElement : childJsonElement.getAsJsonArray()) {
                                arrIndex++
                                /*******************************************************************************************
                                 * Выставим размер динамического массива
                                 *******************************************************************************************/
                                if (childRccEntity.arrayType == ENTITY_ARRAY_TYPE_DYNAMIC) {
                                    log.debug("${rccEntity.smLayoutName}.setSize('$smContextPath${childRccEntity.smLayoutName}', $arrIndex)")
                                    smLayoutsMap.get(childRccEntity.smLayoutName).setSize("$smContextPath${childRccEntity.smLayoutName}", arrIndex)
                                }

                                /*******************************************************************************************
                                 * Выполняем маппинг всех вложенных примитивов
                                 *******************************************************************************************/
                                mapJsonAttributesToSm(arrElement, childRccEntity, smLayoutsMap,
                                        "$jsonContextPath${childRccEntity.entityName}[$arrIndex].",
                                        "$smContextPath${childRccEntity.smLayoutName}[$arrIndex].")

                                /*******************************************************************************************
                                 * Если в описании модели SM_CONF_ENTITY_TREE у текущей сущности есть дочерние сущности,
                                 * то рекурсивно обрабатываем их
                                 *******************************************************************************************/
                                mapJsonTreeToSm(arrElement, childRccEntity, smLayoutsMap,
                                        "$jsonContextPath${childRccEntity.entityName}[$arrIndex].",
                                        "$smContextPath${childRccEntity.smLayoutName}[$arrIndex].")
                            }
                            /*******************************************************************************************
                             * Выставим каунтер массива
                             *******************************************************************************************/
                            log.debug("Setting counter...")
                            setSmVal(smLayoutsMap.get(rccEntity.smLayoutName),
                                    getSmArrayCounterName("$smContextPath${childRccEntity.smLayoutName}"),
                                    arrIndex)


                        }
                    } else {
                        log.info("Entity ${rccEntity.entityName} is not present in input JSON")
                    }

            }
        }
    }

    static void mapJsonAttributesToSm(JsonElement jsonElement, RccEntity rccEntity, Map<String, IHData> smLayoutsMap, String jsonContextPath, String smContextPath) {

        IHData smLayout = smLayoutsMap.get(rccEntity.smLayoutName)
        String entityId = rccEntity.entityName

        ArrayList<HashMap<String, String>> mapConf = globalConfMapInput.get(rccEntity.entityName)
        if (mapConf == null || mapConf?.size() < 1) {
            log.warn("No SM mapping found in $SM_CONF_BATCH_MAP_TABLE_NAME table for entity [$entityId] - SKIP")
            return
        }

        for (Map<String, String> mapEntry : globalConfMapInput.get(entityId)) {
            String smFieldName = getInputSmFieldWithContext(mapEntry.get("SM_FIELD"), smContextPath)
            String jsonFieldName = mapEntry.get("DB_FIELD")
            log.debug("Processing map entry: JSON[$jsonContextPath$jsonFieldName] ==> SM[$smFieldName]")
            JsonElement jsonAttribute = jsonElement?.asJsonObject?.get(jsonFieldName)
            log.info("Get JSON '$jsonContextPath$jsonFieldName' value = $jsonAttribute")
            if (jsonAttribute == null || jsonAttribute?.isJsonNull()) continue
            if (jsonAttribute.isJsonPrimitive()) {
                setSmValFromJson(smLayout, smFieldName, jsonContextPath + jsonFieldName, jsonAttribute.asJsonPrimitive, mapEntry.get("FIELD_TYPE"))
            } else {
                throw new Exception("Unexpected type of JSON element found in message for attribute [$entityId.$jsonFieldName]. " +
                        "Expected type: [JsonPrimitive], actual type: [${jsonAttribute?.getClass()?.getName()}]")
            }
        }

    }

    static void setSmValFromJson(IHData smLayout, String smFieldName, String jsonFieldName, JsonPrimitive jsonFieldVal, String dataType) {
        if (jsonFieldVal.isJsonNull()) return
        Object val = null
        val = jsonFieldVal.getAsString()
        try {
            /*switch (dataType) {
                case "OBJECT":
                    if (jsonFieldVal.isNumber()) val = jsonFieldVal.getAsNumber()
                    else val = jsonFieldVal.getAsString()
                    break
                case "BOOL":

                    String strVal = jsonFieldVal.getAsString()
                    switch (strVal) {
                        case "true": val = 1; break
                        case "false": val = 0; break
                        default: val = null
                    }
                    break
                case "DATE":
                    log.info("jsonFieldVal.getAsString() = ${jsonFieldVal.getAsString()}")
                    if (jsonFieldVal.getAsString() != "null") val = xmlDateStrToDate(jsonFieldVal.getAsString())
                    break
                default:
                    throw new Exception("Unexpected ${SM_CONF_BATCH_MAP_TABLE_NAME}.FIELD_TYPE=$dataType received for JSON field $jsonFieldName")
            }*/
            setSmVal(smLayout, smFieldName, val)
            log.info("Passed JSON value '$jsonFieldName' ==> SM value '${smLayout.getLayout()}:$smFieldName' as $dataType: '$val'")
        } catch (Exception e) {
            String error = "Failed to pass JSON value '$jsonFieldName' ==> SM value '${smLayout.getLayout()}:$smFieldName' as $dataType: $e"
            log.error(error, e)
            throw new Exception(error)
        }
    }

    static Date xmlDateStrToDate(String strDate) {
        try {
            if (!strDate?.trim()) return null
            DateUtils.parseDate(strDate, JSON_DATE_FORMAT, JSON_DATETIME_FORMAT, JSON_DATETIME_MS_FORMAT, JSON_DATETIME_Z_FORMAT)
        } catch (Exception e) {
            throw new Exception("Failed to convert string '$strDate' to date: $e")
        }
    }

    static String dateToXmlDateStr(Date date) {
        try {
            if (date == null) return null
            else return new SimpleDateFormat(JSON_DATE_FORMAT).format(date);
        } catch (Exception e) {
            throw new Exception("Failed to convert date '$date' to String: $e")
        }
    }

    static String dateToXmlDateTimeStr(Date date) {
        try {
            if (date == null) return null
            else return new SimpleDateFormat(JSON_DATE_FORMAT).format(date);
        } catch (Exception e) {
            throw new Exception("Failed to convert date '$date' to String: $e")
        }
    }

    static void fromJsonToSM(LinkedTreeMap map, String name, String LayoutName, Map<String, IHData> smLayoutsMap) {
        map.each {key, value->
            String className = value.getClass().simpleName
            log.info("className = ${value.getClass().simpleName}")
            String name3 = null
            //log.info("key = $key, valueClass = ${value.getClass().simpleName}, props = ${value.properties}")
            if (name == "") {
                name3 = "$key"
            }
            else {
                name3 = name + "." + "$key"
            }
            if (className == "ArrayList") {
                int i = 1
                value.each {value1 ->
                    //log.info ("number111 = ${value1.properties}")
                    String name2 = name3 + "[$i]"
                    smLayoutsMap.get(LayoutName).setSize(name3, i)
                    fromJsonToSM(value1 as LinkedTreeMap, name2, LayoutName, smLayoutsMap)

                    log.debug("${smLayoutsMap.get(LayoutName)}.setSize('$name', $i)")
                    i++
                }

            } else if (className == "LinkedTreeMap") {
                fromJsonToSM(value as LinkedTreeMap, name3, LayoutName, smLayoutsMap)
            }
            else setSmValfromJson(smLayoutsMap.get(LayoutName), name3, value)
            //log.info("name = $name")

        }
    }

}


@CompileStatic
class SmCallException extends Exception {
    SmCallException(String message) {
        super(message)
    }
}

@CompileStatic
class SmPackProcessException extends Exception {
    SmPackProcessException(String message) {
        super(message)
    }
}

@CompileStatic
class SmValidateRccDbException extends Exception {
    SmValidateRccDbException(String message) {
        super(message)
    }
}

@CompileStatic
class SmValidatePcsmDbException extends Exception {
    SmValidatePcsmDbException(String message) {
        super(message)
    }
}

@CompileStatic
class SmInitException extends Exception {
    SmInitException(String message) {
        super(message)
    }
}

@CompileStatic
class SprResponseBuildException extends Exception {
    SprResponseBuildException(String message) {
        super(message)
    }
}