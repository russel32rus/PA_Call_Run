import com.experian.eda.framework.runtime.dynamic.IHData
import com.zaxxer.hikari.HikariDataSource
import groovy.io.FileType
import groovy.beans.Bindable
import groovy.swing.SwingBuilder
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.statement.NamedPreparedStatement

import javax.swing.JButton
import java.awt.*
import javax.management.JMX
import javax.management.MBeanServer
import javax.management.ObjectName
import java.lang.management.ManagementFactory

import com.zaxxer.hikari.HikariPoolMXBean

import static javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import static CustomUtils.*
import static DatabaseHelpers.*
import static RccDictionary.*
import static SmProcessingHelpers.*
import static InitPcsmDbConn.*
import static InitRccDbConn.*
import static ReadGlobalParams.*
import static ExtBatchSaveResultData.*

static void main(String[] args) {

  //Пишем настройки для работы DA, в теории не работает
  //System.setProperty("configDirs", "C:\\Users\\russe\\OneDrive\\Desktop\\PA_Call_Run\\env")
  System.setProperty("client.solution.home", System.getProperty("user.dir"))

  //Удаляем содержимое оутпута чтобы не забивалось
  String folderPath = "output"
  new File(folderPath).eachFile (FileType.FILES) { file ->
    file.delete()
  }

  //Удаляем содержимое logs чтобы не забивалось
  String logsPath = "logs"
  new File(logsPath).eachFile (FileType.FILES) { file ->
    file.delete()
  }

  //Удаляем содержимое input чтобы не забивалось
  String inputPath = "input"
  new File(inputPath).eachFile (FileType.FILES) { file ->
    if (file.name.contains(".json")) file.delete()
  }

  dbConnsPath = "dbConns"
  def dbConnList = []
  new File(dbConnsPath).eachFile (FileType.FILES) { file ->
    dbConnList.add(file.name.toString())
    log.info("file.name = ${file.name.toString()}")
  }
  log.info("dbConnsList = ${dbConnList.toString()}")
  dbConnName = dbConnList[0].toString()

  Integer CurrentCust, AllCusts = 0
  String statusText = "Run PA Call"

  def StageCdList = ["MIN_REQ","BCS_CHECK", "PA_CALC", "CLD1", "CLD2", "RESTRUCT"]
  stageCd = StageCdList[0].toString()

  def swingBuilder = new SwingBuilder()
  swingBuilder.edt {
    lookAndFeel 'nimbus'
    frame(title: 'PA CALL Run', size: [200, 230], show: true, locationRelativeTo: null, defaultCloseOperation: EXIT_ON_CLOSE) {

      borderLayout(vgap: 5)
      panel(border: compoundBorder([emptyBorder(10), titledBorder('Choose Stage')])) {
        runButton = button( id: 'btnRun',
                text: statusText , actionPerformed: {
            initFunc()


        })
        comboBox(
                id:'stageCd',
                items:StageCdList,
                editable:true,
                actionPerformed:{
                  event-> stageCd = event.source.selectedItem

                }
        )
        comboBox(
                id:'dbConn',
                items:dbConnList,
                editable:true,
                actionPerformed:{event-> dbConnName = event.source.selectedItem}
        )
        label(
                  id:'label1',
                  text: "Status : $CurrentCust of $AllCusts"
        )

      }

    }
  }

  if (paCalc != null)
  {

    while (!paCalc.isFinished())
    {

      AllCusts = paCalc.GetAllCust()
      CurrentCust = paCalc.GetCurrCust()

      statusText = "Processing"
      Thread.sleep(100)
    }
    thrCalc.interrupt()
    statusText = "Run PA Calc2"
  }



}
void initFunc()
{
  paCalc = new PACalc(dbConnName, dbConnsPath, stageCd)
  thrCalc   = new Thread(paCalc)
  thrCalc.start()

}
public class PACalc implements Runnable
{
  private volatile Boolean finished = false
  private volatile Integer CurrentCust = 0
  private String dbConnName, dbConnsPath, stageCd
  private volatile int AllCusts = 0
  PACalc(String pDbConnName, String pDbConnsPath, String pStageCd)
  {
    dbConnName = pDbConnName
    dbConnsPath = pDbConnsPath
    stageCd  = pStageCd

  }
  @Override
  public void run()
  {
    String dbConnName1 = dbConnName
    log.info("dbConnName = $dbConnName1")
    dbConnName1 = dbConnsPath + "/" + dbConnName1
    log.info("dbConnName = $dbConnName1")
    /*Вызываем подключения к БД СПР*/
    initPcsmDbConn(dbConnName1)

    /*Вызываем глобальные переменные, переменные окружения и тенанты*/
    readGlobalParams()

    /*Вызываем подключение к БД РКК*/
    initRccDbConn()

    String[] custIds = new File('input\\custIDs.txt')

    AllCusts = custIds.size()


    //Задаем анализируемый вызов
    //String stageCd = formMain.StageComboBoxVal()\
    log.info("stageCd = $stageCd")
    Long StageCdInt = null

    Long customerId = null
    Long prevCustomerId = null

    log.info("Pack started, ${getHeapMemInfo()}")

    /*******************************************************************************************************
     * Получение параметров
     *******************************************************************************************************/
    String smAlias = getSmAliasFromStageCd(stageCd)
    String smSignature = getGlobalParam(GLOBAL_PARAM_BATCH_STRATEGY_SIGNATURE, true)

    //Уже известные значения параметров из SM_CONF (ReadGlobalParams, см InvokeBatchFlow)
    setPackProcessingOptions()

    /* Семафор начала обработки пакета */
    packRunningSemaphore.set(true)



    /*******************************************************************************************************
     * Получаем подключение к БД РКК из общего пула подключений
     *******************************************************************************************************/
    Connection rccConn
    Connection pcsmConn
    try {

      /*******************************************************************************************************
       * Коллекция всех используемых объектов PreparedStatement и связанных с ними ResultSet для получения данных РКК
       *******************************************************************************************************/
      packPreparedStatementsMap = new HashMap<String, NamedPreparedStatement>()
      packResultSetsMap = new ConcurrentHashMap<String, ResultSet>()

      updateSmConfValues()

      /*******************************************************************************************************
       * Создание пула потоков для вызова стратегий
       *******************************************************************************************************/
      decisionAgentPoolExecutor = createDecisionAgentPool(batchThreads, batchQueueSize)

      rccConn = getRccDbConnection()
      pcsmConn =  getPcsmDbConnection()
      int packSize = 0

      CreateCsvFile()

      log.info("CustIDs = ${custIds.toString()}")
      custIds.each {c ->
        CurrentCust++
        customerId = c.toLong()
        log.info("CustIDs starts, custId = $customerId")
        //Получение Pack_Id, Batch_ID, Wave_ID, Pack_Group_By из известных CustId и Stage_Cd
        switch (stageCd) {
          case "MIN_REQ": StageCdInt = 1; break
          case "BCS_CHECK": StageCdInt = 2; break
          case "PA_CALC": StageCdInt = 3; break
          case "CLD1": StageCdInt = 4; break
          case "CLD2": StageCdInt = 5; break
          case "RESTRUCT": StageCdInt = 25; break
          default: StageCdInt = 1
        }
        loadCustomerInfo(rccConn, customerId, StageCdInt)

        currentWaveId.set(waveId.toLong())
        currentBatchId.set(batchId.toLong())
        currentPackId.set(packId.toLong())
        currentBatchId.set(batchId.toLong())

        /*******************************************************************************************************
         * Получаем все записи DICTIONARY_ELEMENT
         *******************************************************************************************************/
        loadRccDictionaryElementsFromDb(rccConn, waveId)

        /*******************************************************************************************************
         *
         * Получаем все записи CUSTOMER текущего пакета (без выполнения запроса, только подготовка)
         *******************************************************************************************************/
        openRccRootStatement(rccConn, SQL_ENTITY_CUSTOMER, customerId, packId, stageCd)

        /*******************************************************************************************************
         * Получаем все записи состояния из таблицы SPR_EXT_CALL_ROUTE (без выполнения запроса, только подготовка)
         *******************************************************************************************************/
        //if (stageCd != SM_STAGES.MIN_REQ.name())  // Кроме вызова SM1 ("MIN_REQ")
        openRccRootStatement(rccConn, SQL_ENTITY_SPR_EXT_CALL_ROUTE, customerId, packId, stageCd)

        /*******************************************************************************************************
         * Рекурсия по всем дочерним сущностям Customer - для открытия курсоров БД по ним (без выполнения запроса, только подготовка)
         *******************************************************************************************************/
        openChildInputStatements(rccConn, pcsmConn, customerId, packId, stageCd, packGroupBy, SQL_ENTITY_CUSTOMER, false)

        /*******************************************************************************************************
         * Фактическое выполнение всех подготовленных запросов в параллельных потоках
         *******************************************************************************************************/
        ConcurrentHashMap<String, Thread> packSqlThreadsMap = new ConcurrentHashMap<String, Thread>()
        final AtomicReference<Exception> exceptionInSqlThread = new AtomicReference<Exception>() // Для передачи исключения из дочернего потока
        ConcurrentHashMap<String, Long> packSqlDurs = new ConcurrentHashMap<>(packPreparedStatementsMap.size())
        log.info("Customer read SQL execution started, ${getHeapMemInfo()}")
        packPreparedStatementsMap.each {
          entityName, ps ->
            log.info("Starting SQL execution thread for entity $entityName...")
            Thread thread = Thread.start("SQL-Exec-$entityName", {
              try {
                log.info("Starting effective SQL execution for entity $entityName...")
                Instant startSql = Instant.now()
                ResultSet rs = ps.executeQuery()
                packResultSetsMap.put(entityName, rs)
                //packSqlDurs.put(entityName, getDurationMs(startSql))
                log.info("$entityName read SQL done")
              } catch (Exception e) {
                RuntimeException runtimeException = new RuntimeException("Error occured when executing select SQL for entity $entityName: ${e.getMessage()}")
                log.error(runtimeException.getMessage(), runtimeException)
                exceptionInSqlThread.set(runtimeException)
              }
            })
            packSqlThreadsMap.put(entityName, thread)
        }

        /*******************************************************************************************************
         * Слияние потоков выполнения SQL запросов
         *******************************************************************************************************/
        String threadName
        packSqlThreadsMap.each {
          entityName, thread ->
            threadName = thread.getName()
            log.info("Waiting for thread '$threadName' to join...")
            thread.join()
            log.info("Joined '$threadName'")
            /*******************************************************************************************************
             * Проверка ошибок выполнения SQL запросов
             *******************************************************************************************************/
            if (exceptionInSqlThread.get() != null) {
              throw exceptionInSqlThread.get()
            }
        }

        /*Comparator<Map.Entry<String, Long>> cmp = new Comparator<Map.Entry<String, Long>>() {

          @Override
          int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
            long v1 = o1.getValue()
            long v2 = o2.getValue()
            return v2-v1
          }
        }

        String strPackSqlDur = packSqlDurs.entrySet().stream()
                .sorted(cmp)
                .map{ Map.Entry<String, Long> entry -> "${entry.getKey()}: ${entry.getValue()}"}
                .collect().toString()
        log.info("Customer read SQL done: $strPackSqlDur, ${getHeapMemInfo()}")
        */


        ResultSet rsCustomer = packResultSetsMap.get(SQL_ENTITY_CUSTOMER)
        dataDepth = 0L
        boolean usePoisonPill = false
        long maxMemory = Runtime.getRuntime().maxMemory() //xmX
        //ExtBatchSaveResultData.outputPsMap = new ConcurrentHashMap<String, NamedPreparedStatement>()
        while (rsCustomer.next()) {
          if (rsCustomer.getLong("CUSTOMER_ID") == customerId) {
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            double usedXmxMemRatio = (double)usedMemory/maxMemory
            if (usedXmxMemRatio > 0.6 && batchQueueSize > batchThreads) {

              //Instant startNewDAPE = Instant.now()
              int oldDAPEQueueSize = decisionAgentPoolExecutor.getQueue().size()
              int oldDAPEApproxTaskCount = decisionAgentPoolExecutor.getActiveCount() as int
              int oldDAPETotalTasks = oldDAPEQueueSize + oldDAPEApproxTaskCount

              String dapePausedLog = "DB2SM mapping paused, usedXmxMemRatio=$usedXmxMemRatio, data depth $dataDepth. Wait old DAPE to do ~$oldDAPETotalTasks tasks (~$oldDAPEQueueSize queued & ~$oldDAPEApproxTaskCount running), ${getHeapMemInfo()}"
              log.info(dapePausedLog)

              //Ждем пока ранее добавленные в TPE таски завершатся...
              decisionAgentPoolExecutor.shutdown()
              decisionAgentPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)

              String oldDAPEdoneLog = "Old DAPE done ~$oldDAPETotalTasks tasks (~$oldDAPEQueueSize queued & ~$oldDAPEApproxTaskCount running) and terminated, ${getHeapMemInfo()}"
              log.info(oldDAPEdoneLog)

              //... и создаем новый TPE с размером очереди равным oldDAPEQueueSize*0.9, но не меньше batchThreads
              batchQueueSize = oldDAPEQueueSize*0.9 as int
              if(batchQueueSize < batchThreads) batchQueueSize = batchThreads
              decisionAgentPoolExecutor = createDecisionAgentPool(batchThreads, batchQueueSize)

              //Для первого клиента нового TPE (т.е. следующего) используем подход poison-pill, чтобы вызвать принудительный executeBatch накопленных данных в БД
              usePoisonPill = true
              String newDAPELog = "New DAPE created with queue capacity $batchQueueSize, ${getHeapMemInfo()}"
              log.info(newDAPELog)
            }

            //TODO узнать для чего и нужен ли?
            //if (stopSemaphore.get()) break

            log.info("Processing next Customer JSON...")
            try {

              int totalRecordsTmp = totalRecords.incrementAndGet()
              log.info("Started processing record #$totalRecordsTmp")

              /*****************************************************************************
               * Создание объектов для вызова SM
               *****************************************************************************/
              Map<String, IHData> smLayoutsMap = new HashMap<String, IHData>()
              IHData smControlData = createSmControlBlock(smAlias, smSignature)
              IHData smCustomerData = addHierarchicalDatasource(SM_LAYOUT_CUSTOMER, smLayoutsMap)
              IHData smDecisionData = addHierarchicalDatasource(SM_LAYOUT_DECISION, smLayoutsMap)
              IHData smStateData = addHierarchicalDatasource(SM_LAYOUT_STATE, smLayoutsMap)
              IHData smTrigMonitoringTask = addHierarchicalDatasource(SM_LAYOUT_TRIG_MONITORING_TASK, smLayoutsMap)
              Exception exceptionHandled = null

              try {
                mapDbRecordToSm(rsCustomer, smLayoutsMap, getGlobalConfSqlEntity(SQL_TREE_INPUT_ROOT, SQL_ENTITY_CUSTOMER), null)

                Exception childException = mapAllDbEntitiesToSm(customerId, packId, packGroupBy, smLayoutsMap,
                        globalConfSqlTreeInput.get(SQL_TREE_INPUT_ROOT).get(SQL_ENTITY_SPR_EXT_CALL_ROUTE), packResultSetsMap, stageCd)
                if (childException != null) exceptionHandled = childException

                //Основной маппинг из базы данных в стратегию - все дочерние сущности CUSTOMER
                globalConfSqlTreeInput.get(SQL_ENTITY_CUSTOMER)?.each {
                  rccEntityName, rccEntity ->
                    if (checkEntityIsActive(rccEntity, packGroupBy, stageCd)) {
                      childException = mapAllDbEntitiesToSm(customerId, packId, packGroupBy, smLayoutsMap, rccEntity, packResultSetsMap, stageCd)
                      if (childException != null) exceptionHandled = childException
                    }
                }
              } catch (Exception e) {
                log.error("Exception handled while executing input mapping from database: $e", e)
                exceptionHandled = e
              }

              /*****************************************************************************
               * Несколько дополнительных полей (сквозная передача в результат)
               *****************************************************************************/
              setSmVal(smDecisionData, SM_FIELD_WAVE_ID, waveId)
              setSmVal(smDecisionData, SM_FIELD_BATCH_ID, batchId)
              setSmVal(smDecisionData, SM_FIELD_PACK_ID, packId)
              setSmVal(smDecisionData, SM_FIELD_CUSTOMER_ID, customerId)
              setSmVal(smDecisionData, SM_FIELD_SPR_PROCESSING_STATUS, SPR_PROCESSING_STATUS_OK)
              setSmVal(smStateData, SM_FIELD_WAVE_ID, waveId)
              setSmVal(smStateData, SM_FIELD_BATCH_ID, batchId)
              setSmVal(smStateData, SM_FIELD_PACK_ID, packId)
              setSmVal(smStateData, SM_FIELD_CUSTOMER_ID, customerId)
              setSmVal(smStateData, SM_FIELD_STAGE_ID, stageCd)
              setSmVal(smStateData, SM_FIELD_RKK_PROCESSING_STATUS, RKK_PROCESSING_STATUS_INIT)
              setSmVal(smStateData, SM_FIELD_EDITION_NUMBER, strategyEditionNumberMap.getOrDefault(smAlias, 0))
              setSmVal(smStateData, SM_FIELD_EDITION_CREATION_DT, strategyCreationDateMap.getOrDefault(smAlias, new Date(0)))
              setSmVal(smStateData, SM_FIELD_EDITION_SOFTWARE_VERSION, strategySmSoftwareVersionMap.getOrDefault(smAlias, "0"))
              setSmVal(smStateData, SM_FIELD_HOST_NAME, HOSTNAME)
              log.info("HOSTNAME = $HOSTNAME")
              setSmVal(smTrigMonitoringTask, SM_FIELD_WAVE_ID, waveId)




              /*****************************************************************************
               * Отправляем блок данных в очередь для вызова SM
               *****************************************************************************/
              IHData[] executionData = [smControlData, smCustomerData, smDecisionData, smStateData, smTrigMonitoringTask]
              RccEntity rootInputEntity = globalConfSqlFlatMapInput.getOrDefault(SQL_TREE_INPUT_ROOT, null)
              if (rootInputEntity == null) throw new Exception("Output root entity '$SQL_TREE_INPUT_ROOT' not found. Please revise entity tree in SM_CONF_ENTITY_TREE table")
              //AtomicInteger childObjNonEmptyAttributesCount = new AtomicInteger(0)
              decisionAgentPoolExecutor.execute(new DecisionAgentTask(executionData, traceFlags, customerId, packId, exceptionHandled, usePoisonPill)) // <--usePoisonPill
              //HashMap<String, IHData> smDataMap1 = smLayoutArrayToMap(executionData)
              //TODO формирование входного json
              /*log.info("JSON IN START")
              JsonElement responseRoot = mapSmTreeToJson(rootInputEntity, smDataMap1, "", "", childObjNonEmptyAttributesCount, globalConfSqlTreeInput, globalConfMapInput)
              String jsonResponse = jsonObjectToString(responseRoot)
              log.info("JSON-IN customerID $jsonResponse")
              def newFile = new File("input/input_${customerId}.json")
              newFile.write(jsonResponse)*/
              usePoisonPill = false //только один клиент с usePoisonPill = true для новосозданного TPE после использования памяти на >60%
              smLayoutsMap = null
              executionData = null //ссылку на executionData выше передали в конструктор таска, значит объект таска ее сохранит

              packSize  = packSize + 1
              prevCustomerId = customerId

            }
            catch (Exception e) {
              failedRecords.incrementAndGet()
              log.error("Failed to process Customer. Exception: $e")
            }

          }
        }

       // ((HikariDataSource)rccDataSource).evictConnection(rccConn)

        //rsCustomer.close()
        //label1.text = "Status : $CurrentCust of $AllCusts"
        //label1.updateUI()
        //JOptionPane.showMessageDialog(new JFrame(), "Status : $CurrentCust of $AllCusts", "Dialog", JOptionPane.INFORMATION_MESSAGE)
      }

      //Формируем инфо-строку о подготовки кастомеров
      String custPrepInfo = "Pack DB to SM mapping done, pack size $AllCusts, data depth $dataDepth, ${getHeapMemInfo()}"
      log.info(custPrepInfo)

      /*****************************************************************************
       * Ожидание завершения работы пула потоков для вызова стратегий
       *****************************************************************************/
      log.info("Waiting for DecisionAgent PoolExecutor to finish...")

      decisionAgentPoolExecutor.shutdown()
      log.info("decisionAgentPoolExecutor.shutdown()")
      decisionAgentPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)
      log.info("decisionAgentPoolExecutor.awaitTermination")

      //long dapeDur = getDurationMs(dapeStart) - totalCustMappingDur
      int dapePoolSize = decisionAgentPoolExecutor.getCorePoolSize()
      int maxQ = ((MyThreadPoolExecutor) decisionAgentPoolExecutor).getMaxQ()

      log.info("DecisionAgent PoolExecutor completed work and shut down")
      String dapeInfo = "Pack DAPE done, pack size $AllCusts. Pool: size=$dapePoolSize, max q $maxQ, tasks done by caller thread $tasksExecutedByCallerThreadCount, ${getHeapMemInfo()}"
      log.info(dapeInfo)

      decisionAgentPoolExecutor.purge()
      decisionAgentPoolExecutor = null
      if (totalRecords.get() == 0 || customerId == 0L) throw new Exception("No data found in database for specified pack")
      if (prevCustomerId == 0L) throw new Exception("All records have failed processing for specified pack. See DB for details")
      if (stopSemaphore.get()) throw new Exception("Fatal error occured, batch processing aborted. Check $SM_PROC_LOG_TABLE_NAME table for details")

      /*****************************************************************************
       * Выполнение всех отложенных запросов (пакетный режим)
       *****************************************************************************/
      //TODO проследить, что соединения с БД все закрыты
      //commitResultBatchAndCloseConnection()
      log.info("Pack done (spr-in-LOCAL, pack size $AllCusts), ${getHeapMemInfo()}")

    }

    catch (Exception e) {
      String errorMessage = "Batch processing of whole Pack failed. Exception: ${e.getMessage()?.trim()}"
      if (!stopSemaphore.get()) // Событие уже опубликовано в DecisionAgentTask
      {
        log.error(errorMessage)
        JOptionPane.showMessageDialog(new JFrame(), "$errorMessage", "Dialog",
                JOptionPane.ERROR_MESSAGE)
      }
    }
    finally {

      stopSemaphore.set(false)
      packRunningSemaphore.set(false)
      /*****************************************************************************
       * Запись всех событий журнала в БД и закрытие курсоров
       *****************************************************************************/
      try {

        log.debug("packPreparedStatementsMap.each -> close()")
        packPreparedStatementsMap.each {
          entity, ps ->
            Connection conn = ps?.getConnection()
            //safeCloseConnection(conn)
            ps?.close()
            //safeCloseConnection(conn)
        }
        packPreparedStatementsMap = null

        log.debug("packResultSetsMap.each -> close()")
        packResultSetsMap.each {
          entity, rs ->
            rs?.close()
        }
        packResultSetsMap = null

        /*log.debug("ExtBatchSaveResultData.outputPsMap.each -> close()")
        ExtBatchSaveResultData.outputPsMap.each {
          entity, ps ->
            ps?.close()
        }
        ExtBatchSaveResultData.outputPsMap = null*/
        log.debug("rccConn.close()")
        rccConn?.close()
        safeCloseConnection(rccConn)
        pcsmConn?.close()
        safeCloseConnection(pcsmConn)
        log.debug("rccConn.close() done")
        JOptionPane.showMessageDialog(new JFrame(), "Done", "Dialog",
                JOptionPane.INFORMATION_MESSAGE)
      } catch (Exception e) {
        log.error("Failed to close some DB objects: $e", e)
        JOptionPane.showMessageDialog(new JFrame(), "Failed to close some DB objects: $e", "Dialog",
                JOptionPane.ERROR_MESSAGE)
      }
      finished = true
    }
  }

  public Boolean isFinished()
  {
    return finished
  }

  public int GetAllCust()
  {
    return AllCusts
  }

  public int GetCurrCust()
  {
    return CurrentCust
  }
}


