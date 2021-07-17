package com.guigu.flink


import java.text.SimpleDateFormat
import java.util.Properties
import java.sql.Timestamp
import org.apache.flink.table.api._
import com.alibaba.fastjson.JSON
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.scala.function.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.bridge.scala.StreamTableEnvironment
import org.apache.flink.table.catalog.hive.HiveCatalog
import org.apache.flink.table.functions.ScalarFunction
import org.apache.flink.types.Row
import redis.clients.jedis.Jedis
import scala.collection.mutable.ArrayBuffer


class KafkaData2Hdfs {

}

object KafkaData2Hdfs {
  def transformTime(time: String): Long = {
    try {
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      val date = format.parse(time.split("\\.")(0))
      date.getTime
    } catch {
      case e: Exception =>
        println(e.printStackTrace())
        0L
    }
  }

  def main(args: Array[String]): Unit = {
    //2.创建main方法，获取StreamExecutionEnvironment运行环境

    val conf = new org.apache.flink.configuration.Configuration()
    import org.apache.flink.configuration.RestOptions
    conf.setString(RestOptions.BIND_PORT, "8081-8089")
    val env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf)
    //    val env = StreamExecutionEnvironment.getExecutionEnvironment
    //3.设置流处理的时间为 EventTime ，使用数据发生的时间来进行数据处理
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    //4.将Flink默认的开发环境并行度设置为1
    env.setParallelism(1)
    //5秒启动一次checkpoint
    env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE)
    //保证程序长时间运行的安全性进行checkpoint操作
    // 设置checkpoint的地址 需要与yml 配置一致state.backend.fs.checkpointdir
    env.setStateBackend(new FsStateBackend("hdfs://hadoop01:9000/data/flink-checkpoints"))
    //设置两次checkpoint的最小时间间隔
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(1000 * 2)
    // checkpoint超时的时长
    env.getCheckpointConfig.setCheckpointTimeout(6000 * 2)
    // 允许的最大checkpoint并行度
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
    //当程序关闭的时，触发额外的checkpoint
    env.getCheckpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION)

    //整合Kafka
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "hadoop01:9092")
    properties.setProperty("zookeeper.connect", "hadoop01:2181")
    properties.setProperty("group.id", "hasdk_comsume_test003")
    properties.setProperty("enable.auto.commit", "true")
    properties.setProperty("auto.commit.interval.ms", "1000")
    properties.setProperty("auto.offset.reset", "earliest")
    //配置序列化和反序列化
    properties.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    properties.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    val consumer = new FlinkKafkaConsumer[String](
      "hasdk_log_test", new SimpleStringSchema(), properties)


    //拿到kafka的数据
    val kafkaDataStream: DataStream[String] = env.readTextFile("G:\\code\\flink_test\\src\\main\\java\\com\\guigu\\flink\\hasdk_log.txt")
    //    val kafkaDataStream: DataStream[String] = env.addSource(consumer)

    //将kafka中的json数据进行封装，转换成成Log样例类
    val hasdkLogStream = kafkaDataStream
      .filter(_.split("\u0001").length == 3)
      .map(
        x => x.split("\u0001")(0)
      ).flatMap(
      jsonStr => {
        val obj = JSON.parseObject(jsonStr)
        val user_id = obj.getString("user_id")
        val event_id = obj.getString("event_id")
        val invited = obj.getString("invited")
        val time_stamp = obj.getString("time_stamp")
        val interested = obj.getString("interested")
        Array(HasdkLog.naFill(HasdkLog(user_id, event_id, invited, time_stamp, interested, new Timestamp(transformTime(time_stamp)))))
      }
    )
    //添加flink的水印处理 , 允许得最大延迟时间是2S
    val watermarkData = hasdkLogStream.assignTimestampsAndWatermarks(
      new BoundedOutOfOrdernessTimestampExtractor[HasdkLog](Time.milliseconds(2000)) {
        override def extractTimestamp(element: HasdkLog): Long = {
          transformTime(element.time_stamp)
        }
      }
    )

    //设置窗口大小
    val aggStream = watermarkData
      .timeWindowAll(Time.seconds(10L))
      .process( //窗口聚合,内部进行去重逻辑
        new ProcessAllWindowFunction[HasdkLog, Array[HasdkLog], TimeWindow] {
          override def process(context: Context, elements: Iterable[HasdkLog], out: Collector[Array[HasdkLog]]): Unit = {
            out.collect(elements.toSet.toArray) //使用set对其去重
          }
        }).flatMap(x => x)
    //    clickStream.print()
    val showLabel = aggStream.timeWindowAll(Time.seconds(10L))
      .process( //窗口聚合,内部进行Label标签
        new ProcessAllWindowFunction[HasdkLog, Array[(Int, HasdkLog)], TimeWindow] {
          override def process(context: Context, elements: Iterable[HasdkLog], out: Collector[Array[(Int, HasdkLog)]]): Unit = {
            val click = elements.filter(_.interested.equals("1")).toArray
            val show = elements.filter(_.interested.equals("0")).toArray
            val clickIds = click.map(_.user_id)
            val logs1 = new ArrayBuffer[HasdkLog]()
            //留存问题,为什么只能使用新的数组在原有的数组做filter结果不对?
            show.foreach(x => {
              if (!clickIds.contains(x.user_id)) logs1.append(x)
            })
            out.collect(logs1.toArray.map((0, _)))
          }
        }).flatMap(x => x)


    /**
     * alter table train_test_flink set TBLPROPERTIES ('is_generic'='false');
     * alter table train_test_flink set TBLPROPERTIES ('sink.partition-commit.policy.kind'='metastore');
     * //如果想使用eventtime分区
     * alter table train_test_flink set TBLPROPERTIES ('sink.partition-commit.trigger'='partition-time');
     * alter table train_test_flink set TBLPROPERTIES ('partition.time-extractor.timestamp-pattern'='$pt_d');
     */
    val tblEnv = StreamTableEnvironment.create(executionEnvironment = env)
    val catalog = new HiveCatalog("hive", "events", "G:\\code\\flink_test\\src\\main\\resources\\hive-3.1.0", "3.1.2")
    //        val catalog = new HiveCatalog("hive", "events", "G:\\code\\flink_test\\src\\main\\resources", "1.1.0")
    import org.apache.flink.table.api.SqlDialect
    tblEnv.registerCatalog("hive", catalog)
    tblEnv.useCatalog("hive")
    tblEnv.getConfig.setSqlDialect(SqlDialect.HIVE)

    val configs = Array("field1", "field2", "field3", "field4") :+ "field5" :+ "field6" :+"field7":+"field8"

    val rawStream = showLabel.map(_._2)
      .map(
        log => {
          val jedis = new Jedis("hadoop01", 6379)
          val mymap = new java.util.HashMap[String, String]()

          configs.foreach(x => {
            val value = jedis.hget(log.user_id, x)
            mymap.put(x, value)
          }
          )
          (log, mymap)
        })
    tblEnv.registerDataStream("union_table", rawStream, 'hasdk, 'mymap)
    tblEnv.registerFunction("getValFromMap", new MapVal())
    val assembleFields = configs.map(x => s"getValFromMap(mymap,'$x') as $x").mkString(",")
    val result = tblEnv.sqlQuery(
      s"""
         |select  hasdk.*,$assembleFields from  union_table
         |""".stripMargin)
    val nowClolumns = result.getSchema.getFieldNames
    val oldColumns = tblEnv.sqlQuery("select * from train_test_flink_raw limit 1").getSchema.getFieldNames.filter(x => x != "pt_d")
    val newColumns = nowClolumns.diff(oldColumns).filter(_ != "timeStamp_flag")
    if (!newColumns.isEmpty) {
      val alterSql =
        s"""
           |alter table train_test_flink_raw add  columns(${newColumns.mkString("", " STRING ,", " STRING")})
           |""".stripMargin

      println(alterSql)
      tblEnv.executeSql(alterSql)
    }
    val sortColumns = oldColumns.union(newColumns)
    implicit val typeInfo: TypeInformation[Row] = TypeInformation.of(classOf[Row])
    val value = tblEnv.toAppendStream(result)
    value.map(x => ("验证最终值", x)) print()
    tblEnv.registerDataStream("raw_table", value)
    println(sortColumns.mkString(","))
    val insertSql =
      s"""
         |insert into table events.train_test_flink_raw
         |select ${sortColumns.mkString(",")}
         |,DATE_FORMAT(timeStamp_flag, 'yyyyMMdd') as pt_d
         |from raw_table
         |""".stripMargin
    println(insertSql)
    val names = tblEnv.executeSql(insertSql).getTableSchema.getFieldNames
    println(names.mkString(","))
    env.execute("insert to hive")
  }

  class MapVal extends ScalarFunction {
    def eval(obj: java.util.HashMap[String, String], key: String): String = {
      obj.get(key)
    }
  }

}
