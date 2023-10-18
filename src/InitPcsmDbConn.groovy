/*******************************************************************************************************
 * Класс-компонент InitPcsmDbConn.
 * Используется в овновном батч-процессе для создания пула подключений к внутренней БД СПР
 *******************************************************************************************************/

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

import static CustomUtils.*
import static DatabaseHelpers.*

@CompileStatic
class InitPcsmDbConn {

    private static final Logger log = LoggerFactory.getLogger(this)
    //TODO стояли catch на ошибки, неоходимо также смотреть ошибки
    /*****************************************************************************
     * Конструктор
     *****************************************************************************/
    static void initPcsmDbConn(String pathName) {

        File pcsmDB = new File(pathName)
        /*****************************************************************************
         *  Чтение параметров подключения к внутренней БД СПР из переменных окруежения
         *****************************************************************************/
        log.info("Getting PCSM connection settings from Environment Variables...")
        String url = getEnvironmentVariable("SPR_PSCM_DB_URL")
        String user = getEnvironmentVariable("SPR_PCSM_DB_USER")
        String pass = getEnvironmentVariable("SPR_PCSM_DB_PASS")
        String driver = getEnvironmentVariable("SPR_PCSM_DB_DRIVER")
        String schema = getEnvironmentVariable("SPR_PCSM_DB_SCHEMA")
        if (url?.trim() && user?.trim() && pass?.trim() && driver?.trim()) {
            pcsmDataSource = createDataSource("pcsm-pool", url, user, pass, schema, driver)
        } else {

            log.info("Not all PCSM connection set in Environment Variables. Skip and proceed to tenant.properties")
            /*****************************************************************************
             *  Чтение параметров подключения к внутренней БД СПР из файла tenant.properties
             *****************************************************************************/
            log.info("Getting PCSM connection settings from tenant.properties...")
            pcsmDB.withInputStream {
                pcsmDBprops.load(it)
            }
            url = pcsmDBprops.getProperty("db.url")
            user = pcsmDBprops.getProperty("db.user")
            pass = pcsmDBprops.getProperty("db.pass")
            driver = pcsmDBprops.getProperty("db.driver")
            schema = pcsmDBprops.getProperty("db.schema")
            if (url?.trim()) {
                pcsmDataSource = createDataSource("pcsm-pool", url, user, pass, schema, driver)
            } else {

                String host = getTenantProperty("db.host", true)
                String port = getTenantProperty("db.port", true)
                String sid = getTenantProperty("db.sid", true)
                String hasStandby = getTenantProperty("db.hasStandby", false)
                if ("true".equals(hasStandby)) {
                    String hostStandby = getTenantProperty("db.host.standby", true)
                    String portStandby = getTenantProperty("db.port.standby", true)
                    /*****************************************************************************
                     *  Создание пула подключений к внутренней БД СПР
                     *****************************************************************************/
                    //pcsmDataSource = createDataSourceWithFailover(host, port, hostStandby, portStandby, user, pass, sid, schema, driver)
                } else {
                    //pcsmDataSource = createDataSource(host, port, user, pass, sid, schema, driver)
                }
            }
        }

        if (pcsmDataSource == null) throw new Exception("Failed to create PCSM database connection pool!")

        log.info("Trying to open test PCSM DB connection...")
        Connection conn = getConnection(pcsmDataSource)
        conn.close()
    }

}
