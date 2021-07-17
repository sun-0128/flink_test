package com.guigu.flink

import java.io.File
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util
import redis.clients.jedis.Jedis
import org.apache.flink.streaming.api.scala._
import com.alibaba.fastjson.JSON
import com.guigu.flink.utils.{ParametersConfig, RedisUtils}
import org.apache.flink.api.common.functions.{IterationRuntimeContext, RichFunction, RichMapFunction, RuntimeContext}
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}


object TestParameterTools {
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
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    //    val conf = new org.apache.flink.configuration.Configuration()
    //    import org.apache.flink.configuration.RestOptions
    //    conf.setString(RestOptions.BIND_PORT, "8081-8089")
    //    val env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf)
    val params = ParameterTool.fromArgs(args)

    val file = params.get("configFile")
    val tools = ParameterTool.fromPropertiesFile(file)
    //初始化参数
    ParametersConfig.initConfig(tools)
    env.registerCachedFile("file:///data/swx1007553/redis.properties", "cache", true)




    //3.设置流处理的时间为 EventTime ，使用数据发生的时间来进行数据处理
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    //4.将Flink默认的开发环境并行度设置为1
    env.setParallelism(ParametersConfig.PARALLELISM)
    //5秒启动一次checkpoint
    env.enableCheckpointing(ParametersConfig.CHECKPOINT_INTERVAL, CheckpointingMode.EXACTLY_ONCE)
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

    env.getConfig.setGlobalJobParameters(params)
    val dataStream: DataStream[String] = env.readTextFile(ParametersConfig.DATA_PATH)
    val logStream = dataStream.filter(_.split("\u0001").length == 3)
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

    val assemble = logStream
      .map(
        new RichMapFunction[HasdkLog, (HasdkLog, java.util.HashMap[String, String])] {

          var file: File = null
          var jedis: Jedis = null

          override def open(parameters: Configuration): Unit = {

            file = getRuntimeContext.getDistributedCache.getFile("cache")
            jedis = RedisUtils.getClient(file)
          }


          override def close(): Unit = {

            if (jedis != null) jedis.close()
            if (file != null) file.deleteOnExit()
          }

          //逻辑
          override def map(log: HasdkLog): (HasdkLog, util.HashMap[String, String]) = {

            val mymap = new util.HashMap[String, String]()
            Array("field1", "field2").foreach(x => {
              val value = jedis.hget(log.user_id, x)
              mymap.put(x, value)
            }
            )
            (log, mymap)
          }
        }
      )
    println("开始")
    assemble.print()
    println("结束")
    env.execute("hahaha")

  }
}


