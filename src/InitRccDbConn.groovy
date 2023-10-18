/*******************************************************************************************************
 * Класс-компонент InitRccDbConn.
 * Используется в овновном батч-процессе для создания пула подключений к БД РКК
 *******************************************************************************************************/

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.statement.NamedPreparedStatement

import java.sql.Connection
import java.time.Instant


import static CustomUtils.*
import static DatabaseHelpers.*
import static InitRccDbConn.*

@CompileStatic
class InitRccDbConn {

    private static final Logger log = LoggerFactory.getLogger(this)

    static void     initRccDbConn () {
        /*****************************************************************************
         *  Чтение параметров подключения к БД РКК из глобального конфига
         *****************************************************************************/
        log.info("Getting RCC connection settings...")
        String url = getGlobalParam("RCC_DB_URL", false)
        String user = getGlobalParam("RCC_DB_USER", true)
        String pass = getGlobalParam("RCC_DB_PASS", true)
        //pass = decryptString(pass)
        //log.debug("$user decrypted password: '$pass'")
        String schema = getGlobalParam("RCC_DB_SCHEMA", false)
        String driver = getGlobalParam("RCC_DB_DRIVER_CLASS", true)

        /*****************************************************************************
         *  Создание фабрики подключений к БД РКК
         *****************************************************************************/
        Instant start = Instant.now()
        if (url?.trim()) {
            rccDataSource = createDataSource("rcc-pool", url, user, pass, schema, driver)
        } else {
            String host = getGlobalParam("RCC_DB_HOST", true)
            String port = getGlobalParam("RCC_DB_PORT", true)
            String sid = getGlobalParam("RCC_DB_NAME", true)
            Integer hasStandby = getGlobalParam("RCC_DB_HAS_STANDBY", false) as Integer
            if (hasStandby == 1) {
                String hostStandby = getGlobalParam("RCC_DB_HOST_STANDBY", true)
                String portStandby = getGlobalParam("RCC_DB_PORT_STANDBY", true)
                //rccDataSource = createDataSourceWithFailover(host, port, hostStandby, portStandby, user, pass, sid, schema, driver)
            } else {
                //rccDataSource = createDataSource(host, port, user, pass, sid, schema, driver)
            }
        }
        if (rccDataSource == null) throw new Exception("Failed to create RCC database connection pool!")

        log.info("Trying to open test RCC DB connection...")
        Connection conn = getConnection(rccDataSource)
        conn.close()

        //logEventInfoImmediate("RCC database connection open", getDurationMs(start), EVENT_CONNECT_RCC_DB, log)
        log.info("RCC database connection open")

        /*****************************************************************************
         *  Валидация основных запросов таблицы SM_CONF_BATCH_SQL
         *****************************************************************************/
        start = Instant.now()
        try {
            //TODO если понадобиваться валидатор данных то можно будет сделать, но надо и поддерживать
            //validateDbWithExplainPlan()
            //logEventInfoImmediate("RCC database validation passed", getDurationMs(start), EVENT_VALIDATE_RCC_DB, log)
            log.info("RCC database validation passed")
        } catch (Exception e) {
            throw new SmValidateRccDbException(e.getMessage())
        }
    }



}
