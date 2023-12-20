import com.experian.eda.framework.runtime.dynamic.HierarchicalNode
import com.experian.eda.framework.runtime.dynamic.IHData
import com.google.gson.*
import com.google.gson.internal.LinkedTreeMap
import groovy.io.FileType

import java.awt.*
import org.statement.NamedPreparedStatement

import static javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.JFrame
import javax.swing.JOptionPane

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static CustomUtils.*
import static DatabaseHelpers.*
import static RccDictionary.*
import static SmProcessingHelpers.*
import static InitPcsmDbConn.*
import static InitRccDbConn.*
import static ReadGlobalParams.*
import static ExtBatchSaveResultData.*
import static JsonProcessingHelpers.*

static void main(String[] args) {

  //Пишем настройки для работы DA, в теории не работает
  //System.setProperty("configDirs", "C:\\Users\\russe\\OneDrive\\Desktop\\PA_Call_Run\\env")
  System.setProperty("client.solution.home", System.getProperty("user.dir"))

  //Удаляем содержимое оутпута чтобы не забивалось
  String folderPath = "output"
  if (new File(folderPath).exists()) {
    new File(folderPath).eachFile (FileType.FILES) { file ->
      file.delete()
    }
  } else {
    new File(folderPath).mkdirs()
  }

  //Удаляем содержимое logs чтобы не забивалось
  String logsPath = "logs"
  if (new File(logsPath).exists()) {
    new File(logsPath).eachFile (FileType.FILES) { file ->
      file.delete()
    }
  } else {
    new File(logsPath).mkdirs()
  }

  dbConnsPath = "dbConns"
  def dbConnList = []
  if (new File(dbConnsPath).exists()) {
    new File(dbConnsPath).eachFile (FileType.FILES) { file ->
      dbConnList.add(file.name.toString())
      log.info("file.name = ${file.name.toString()}")
    }
  }
  log.info("dbConnsList = ${dbConnList.toString()}")
  dbConnName = dbConnList[0].toString()


  custIds = null

  def StageCdList = ["MIN_REQ","BCS_CHECK", "PA_CALC", "CLD1", "CLD2", "RESTRUCT"]
  stageCd = StageCdList[0].toString()

  swingBuilder1.edt {
    lookAndFeel 'nimbus'
    frame(id: 'frame', title: 'PA CALL Run', size: [230, 350], show: true, locationRelativeTo: null, defaultCloseOperation: EXIT_ON_CLOSE) {

      borderLayout(vgap: 5)
      tabbedPane(border: compoundBorder([emptyBorder(10)]), ) {
        panel(id: 'panel1', name: "Download Json", constraints: BorderLayout.CENTER,  border: compoundBorder([emptyBorder(10), titledBorder('Choose Stage')])) {
          label(
                  id: 'downlaodStatusLabel',
                  text: 'Preparing',
                  border: compoundBorder([emptyBorder(2)])
          )
          runButton = button(id: 'btnRun',
                  text: 'Download Json', actionPerformed: {
            custIds = getStringArrFromChar(CustomerIn.text)
            statusLabel = "In Work"
            downlaodStatusLabel.setText(statusLabel)
            initFunc()
          })
          comboBox(
                  id: 'stageCd',
                  items: StageCdList,
                  editable: true,
                  actionPerformed: {
                    event -> stageCd = event.source.selectedItem

                  }
          )
          comboBox(
                  id: 'dbConn',
                  items: dbConnList,
                  actionPerformed: { event -> dbConnName = event.source.selectedItem }
          )
          textArea(
                  id: 'CustomerIn',
                  editable: true
          )

        }
        panel(name: "Running Json", border: compoundBorder([emptyBorder(10)])) {
          label(
                  id: 'Statuslabel',
                  text: 'Preparing',
                  border: compoundBorder([emptyBorder(2)])
          )
          runButton = button(id: 'btnRun',
                  text: 'Run PA Call', actionPerformed: {
            statusLabel = "In Work"
            Statuslabel.setText(statusLabel)
            initPaCalc()
          })
          checkBox(
                  id: 'DaLogEnabled',
                  text: 'Need DaLog?'
          )
          checkBox(
                  id: 'CsvOutputEnabled',
                  text: 'Need Csv?'
          )
        }
      }

    }

  }

  /*if (paCalc != null)
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
  }*/



}
void initFunc()
{
  paDown = new PADown(dbConnName, dbConnsPath, stageCd, custIds)
  thrDown   = new Thread(paDown)
  thrDown.start()

}

public class PADown implements Runnable {
  private volatile Boolean finished = false
  private volatile Integer CurrentCust = 0
  private String dbConnName, dbConnsPath, stageCd
  private volatile int AllCusts = 0

  PADown(String pDbConnName, String pDbConnsPath, String pStageCd, String[] pcustIds) {
    dbConnName = pDbConnName
    dbConnsPath = pDbConnsPath
    stageCd = pStageCd
    custIds = pcustIds
  }

  @Override
  public void run() {
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

    //custIds = swingBuilder.CustomerIn.text
    //custIds = new File('input\\custIDs.txt')

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



      rccConn = getRccDbConnection()
      pcsmConn = getPcsmDbConnection()

      //CreateCsvFile()

      log.info("CustIDs = ${custIds.toString()}")
      custIds.each { c ->
        boolean custIsFinished = false
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
        loadCustomerInfo(rccConn, customerId, StageCdInt, stageCd)

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
                packSqlDurs.put(entityName, getDurationMs(startSql))
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

        Comparator<Map.Entry<String, Long>> cmp = new Comparator<Map.Entry<String, Long>>() {

          @Override
          int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
            long v1 = o1.getValue()
            long v2 = o2.getValue()
            return v2 - v1
          }
        }

        String strPackSqlDur = packSqlDurs.entrySet().stream()
                .sorted(cmp)
                .map { Map.Entry<String, Long> entry -> "${entry.getKey()}: ${entry.getValue()}" }
                .collect().toString()
        log.info("Customer read SQL done: $strPackSqlDur, ${getHeapMemInfo()}")


        ResultSet rsCustomer = packResultSetsMap.get(SQL_ENTITY_CUSTOMER)
        dataDepth = 0L
        log.info("Before while rs.Cust")
        while (rsCustomer.next() && !custIsFinished) {
          log.info("rsCustomer = ${rsCustomer.getLong("CUSTOMER_ID")}; customerId = $customerId")
          if (rsCustomer.getLong("CUSTOMER_ID") == customerId) {


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


              log.info("JSON IN START")
              JsonObject jsonIn = mapSmToJson(executionData)

              String jsonResponse = jsonObjectToString(jsonIn)
              log.info("JSON-IN customerID $jsonResponse")
              def newFile = new File("input/input_${customerId}.json")
              newFile.write(jsonResponse)

              //usePoisonPill = false
              //только один клиент с usePoisonPill = true для новосозданного TPE после использования памяти на >60%
              smLayoutsMap = null
              executionData = null
              //ссылку на executionData выше передали в конструктор таска, значит объект таска ее сохранит

            }
            catch (Exception e) {
              failedRecords.incrementAndGet()
              log.error("Failed to process Customer. Exception: $e")
            }
            custIsFinished = true
          }

        }

      }

      //Формируем инфо-строку о подготовки кастомеров
      String custPrepInfo = "Pack DB to SM mapping done, pack size $AllCusts, data depth $dataDepth, ${getHeapMemInfo()}"
      log.info(custPrepInfo)

      if (totalRecords.get() == 0 || customerId == 0L) throw new Exception("No data found in database for specified pack")
      if (prevCustomerId == 0L) throw new Exception("All records have failed processing for specified pack. See DB for details")
      if (stopSemaphore.get()) throw new Exception("Fatal error occured, batch processing aborted. Check $SM_PROC_LOG_TABLE_NAME table for details")

      /*****************************************************************************
       * Выполнение всех отложенных запросов (пакетный режим)
       *****************************************************************************/

      //commitResultBatchAndCloseConnection()
      log.info("Pack done (spr-in-LOCAL, pack size $AllCusts), ${getHeapMemInfo()}")

    }

    catch (Exception e) {
      String errorMessage = "Batch processing of whole Pack failed. Exception: ${e.getMessage()?.trim()}"
      if (!stopSemaphore.get()) // Событие уже опубликовано в DecisionAgentTask
      {
        log.error(errorMessage)
        JOptionPane.showMessageDialog(new JFrame(), "$errorMessage", "Dialog", JOptionPane.ERROR_MESSAGE)
        statusLabel = "Error"
        swingBuilder1.downlaodStatusLabel.setText(statusLabel)
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
            //Connection conn = ps?.getConnection()
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
        //JOptionPane.showMessageDialog(new JFrame(), "Done", "Dialog", JOptionPane.INFORMATION_MESSAGE)
        statusLabel = "Done"
        swingBuilder1.downlaodStatusLabel.setText(statusLabel)
      } catch (Exception e) {
        log.error("Failed to close some DB objects: $e", e)
        statusLabel = "Error"
        swingBuilder1.downlaodStatusLabel.setText(statusLabel)
      }
      finished = true
    }
  }

  static JsonObject mapSmToJson(IHData[] data1) {

    JsonObject jsonIn = new JsonObject()
    data1.each { data ->
      JsonObject jsonElement = new JsonObject()
      log.info("layout = ${data.getLayout().toString()}")
      HierarchicalNode data2 = data.rootNode
      jsonElement = mapNodeToJson(data2)
      jsonIn.add(data.getLayout().toString(), jsonElement)
      log.info("add json ${jsonObjectToString(jsonElement)}")
    }

    return jsonIn
  }

  static JsonObject mapNodeToJson(HierarchicalNode node) {

    JsonObject jsonElement = new JsonObject()
    ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>()
    node.childNodeMap.each { name, obj ->
      HashMap<String, Object> map = new HashMap<String, Object>()
      map.put("name", name)
      map.put("object", obj)
      list.add(map)
    }
    for (HashMap<String, Object> map : list) {
      String classname = map.get("object").getClass().simpleName
      String name = map.get("name").toString()
      log.info("name = $name, className = $classname")
      if (classname == "ValueLeafNode") {
        log.info("typeClass value = ${map.get("object").getValue().getClass().simpleName}")
        switch (map.get("object").getValue().getClass().simpleName) {
          case "Date":
            String val = dateToXmlDateStr(map.get("object").getValue() as Date)
            //val = xmlDateStrToDate(val)
            jsonElement.addProperty(name, val)
            break
          case "Timestamp":
            String val = dateToXmlDateTimeStr(map.get("object").getValue() as Date)
            //val = xmlDateStrToDate(val)
            jsonElement.addProperty(name, val)
            break
          case "BigDecimal":
            Number val = map.get("object").getValue() as Number
            if (val != null) jsonElement.addProperty(name, val)
            break
          case "NullObject":
            //String val = ""
            //jsonElement.addProperty(name, val)
            break
          case "Integer":
            Number val = map.get("object").getValue() as Number
            if (val != null) jsonElement.addProperty(name, val)
            break
          case "Long":
            Number val = map.get("object").getValue() as Number
            if (val != null) jsonElement.addProperty(name, val)
            break
          default:
            String val = map.get("object").getValue() as String
            if (val != null) jsonElement.addProperty(name, val)
        }
      }
      if (classname == "MetadataLeafNode") {
        Integer arrCount = map.get("object").size
        Integer arrIndex = 0
        log.info("arrCount = ${arrCount}")
        if (arrCount > 0) {
          JsonArray childJsonArray = new JsonArray()
          for (arrIndex = 1; arrIndex <= arrCount; arrIndex++) {
            JsonObject childJsonElement = new JsonObject()
            for (HashMap<String, Object> map1 : list) {
              if (map1.get("name") == "$name[$arrIndex]") {
                childJsonElement = mapNodeToJson(map1.get("object") as HierarchicalNode)
                childJsonArray.add(childJsonElement)
              }
            }
          }
          jsonElement.add(name, childJsonArray)
          log.info("add json inside ${jsonObjectToString(jsonElement)}")
        }
      }
      if (classname == 'HierarchicalNode' && !(name.contains('[') && name.contains(']'))) { //Для сущностей не массивов
        JsonObject childJsonElement = new JsonObject()
        childJsonElement = mapNodeToJson(map.get("object") as HierarchicalNode)
        //JsonArray childJsonArray = new JsonArray()
        //childJsonArray.add(childJsonElement)
        jsonElement.add(name, childJsonElement)
      }
    }

    return jsonElement
  }

}

void initPaCalc() {
  paCalc = new PACalc()
  thrCalc   = new Thread(paCalc)
  thrCalc.start()
}

class PACalc implements Runnable {

  private volatile Boolean finished = false
  public static Integer CurrentCust = 0
  public volatile int AllCusts = 0

  PACalc() {
  }

  @Override
  void run() {
    try {
      //Берем каждый файл JSON и прогоняем по стратегии
      String inputPath = "input"
      def jsonList = []
      if (new File(inputPath).exists()) {
        new File(inputPath).eachFile(FileType.FILES) { file ->
          if (file.name.contains(".json")) {
            Gson gson = new Gson()
            Map j = gson.fromJson(new FileReader(file), Map.class)
            jsonList.add(j)
          }
        }
      }
      AllCusts = jsonList.size()

      /*******************************************************************************************************
       * Создание пула потоков для вызова стратегий
       *******************************************************************************************************/
      decisionAgentPoolExecutor = createDecisionAgentPool(4, 1000)

      if (swingBuilder1.DaLogEnabled.selected == true) traceFlags = 31 else traceFlags = 0
      if (swingBuilder1.CsvOutputEnabled.selected == true) csvOutput = true else csvOutput = false

      if (csvOutput == true) {
        CreateCsvFile()
      }

      jsonList.each { json ->
        CurrentCust++

        Map<String, IHData> smLayoutsMap = new HashMap<String, IHData>()
        IHData smControlData = addHierarchicalDatasource("OCONTROL", smLayoutsMap)
        IHData smCustomerData = addHierarchicalDatasource(SM_LAYOUT_CUSTOMER, smLayoutsMap)
        IHData smDecisionData = addHierarchicalDatasource(SM_LAYOUT_DECISION, smLayoutsMap)
        IHData smStateData = addHierarchicalDatasource(SM_LAYOUT_STATE, smLayoutsMap)
        IHData smTrigMonitoringTask = addHierarchicalDatasource(SM_LAYOUT_TRIG_MONITORING_TASK, smLayoutsMap)
        Exception exceptionHandled = null

        IHData[] executionData = [smControlData, smCustomerData, smDecisionData, smStateData, smTrigMonitoringTask]


        json.each { it ->
          String LayoutName = it.key
          LinkedTreeMap map = new LinkedTreeMap()
          map = it.value
          fromJsonToSM(map, "", LayoutName, smLayoutsMap)
        }

        Long customerId = getSmVal(smLayoutsMap.get("DECISION_RESULT"), "CUSTOMER_ID") as Long
        packId = getSmVal(smLayoutsMap.get("DECISION_RESULT"), "PACK_ID") as int
        String alias = getSmVal(smLayoutsMap.get("OCONTROL"), "ALIAS") as String
        setSmValfromJson(smLayoutsMap.get("OCONTROL"), SM_FIELD_EDITION_NUMBER, strategyEditionNumberMap.getOrDefault(alias, 0))
        setSmValfromJson(smLayoutsMap.get("OCONTROL"), SM_FIELD_EDITION_CREATION_DT, strategyCreationDateMap.getOrDefault(alias, new Date(0)))
        setSmValfromJson(smLayoutsMap.get("OCONTROL"), SM_FIELD_EDITION_SOFTWARE_VERSION, strategySmSoftwareVersionMap.getOrDefault(alias, "0"))
        log.info("CustIDs starts, custId = $customerId")
        boolean usePoisonPill = false

        try {

          loadStrategy(alias)

          decisionAgentPoolExecutor.execute(new DecisionAgentTask(executionData, traceFlags, customerId, packId, exceptionHandled, usePoisonPill))
          // <--usePoisonPill

          usePoisonPill = false
          //только один клиент с usePoisonPill = true для новосозданного TPE после использования памяти на >60%
          smLayoutsMap = null
          executionData = null
          //ссылку на executionData выше передали в конструктор таска, значит объект таска ее сохранит

          //JOptionPane.showMessageDialog(new JFrame(), "$CurrentCust of $AllCusts Done", "Dialog", JOptionPane.INFORMATION_MESSAGE)
          statusLabel = "In work ($CurrentCust of $AllCusts)"
          swingBuilder1.Statuslabel.setText(statusLabel)
        }
        catch (Exception e) {
          failedRecords.incrementAndGet()
          log.error("Failed to process Customer. Exception: $e")
        }
        log.info("Processing next Customer JSON...")

      }

      //Формируем инфо-строку о подготовки кастомеров
      String custPrepInfo = "Pack DB to SM mapping done, pack size $AllCusts,  ${getHeapMemInfo()}"
      log.info(custPrepInfo)

      decisionAgentPoolExecutor.shutdown()
      decisionAgentPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)

      log.info("DecisionAgent PoolExecutor completed work and shut down")

      decisionAgentPoolExecutor.purge()
      decisionAgentPoolExecutor = null
      if (AllCusts == 0) throw new Exception("No data")
      if (stopSemaphore.get()) throw new Exception("Fatal error occured, batch processing aborted. Check $SM_PROC_LOG_TABLE_NAME table for details")

    }
    catch (Exception e) {
      String errorMessage = "Batch processing of whole Pack failed. Exception: ${e.getMessage()?.trim()}"
      if (!stopSemaphore.get()) // Событие уже опубликовано в DecisionAgentTask
      {
        log.error(errorMessage)
        JOptionPane.showMessageDialog(new JFrame(), "$errorMessage", "Dialog", JOptionPane.ERROR_MESSAGE)
        statusLabel = "Error"
        swingBuilder1.Statuslabel.setText(statusLabel)
      }
    }
    finally {

      stopSemaphore.set(false)
      packRunningSemaphore.set(false)
      //JOptionPane.showMessageDialog(new JFrame(), " All Done", "Dialog", JOptionPane.INFORMATION_MESSAGE)
      statusLabel = "Done"
      swingBuilder1.Statuslabel.setText(statusLabel)

      finished = true
    }
  }

}