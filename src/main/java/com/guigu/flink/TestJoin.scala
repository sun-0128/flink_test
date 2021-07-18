package com.guigu.flink

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import com.alibaba.fastjson.JSON
import com.guigu.flink.KafkaData2Hdfs.transformTime
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, StateTtlConfig, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.time.Time
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

object TestJoin {
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
    //拿到数据
    val dataStream: DataStream[String] = env.readTextFile("G:\\code\\code\\flink_test\\src\\main\\java\\com\\guigu\\flink\\hasdk_log2.txt")


    val hasdkLogStream = dataStream
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
      .assignTimestampsAndWatermarks(
        new BoundedOutOfOrdernessTimestampExtractor[HasdkLog](org.apache.flink.streaming.api.windowing.time.Time.milliseconds(2000)) {
          override def extractTimestamp(element: HasdkLog): Long = {
            transformTime(element.time_stamp)
          }
        }
      )

    val distinctClick = hasdkLogStream.filter(_.interested == "1").keyBy(_.user_id).process(
      new DistinctFunction()

    )
    val distinctShow = hasdkLogStream.filter(_.interested == "0").keyBy(_.user_id).process(
      new DistinctFunction()
    )
    //正负样本生成
    val sample = distinctShow.keyBy(_.user_id).connect(
      distinctClick.keyBy(_.user_id)
    ).process(new CoProcessJoin())
    //    sample.print()

    env.execute("hahahah")
  }


  class DistinctFunction extends KeyedProcessFunction[String, HasdkLog, HasdkLog] {
    private var state: ValueState[MyState] = null
    private var stateDescriptor: ValueStateDescriptor[MyState] = null

    override def open(parameters: Configuration): Unit = {
      stateDescriptor = new ValueStateDescriptor[MyState]("mystate", classOf[MyState])
      stateDescriptor.enableTimeToLive(
        StateTtlConfig.newBuilder(org.apache.flink.api.common.time.Time.seconds(30))
          .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
          .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
          .disableCleanupInBackground()
          .build)
      state = getRuntimeContext.getState(stateDescriptor)
    }

    override def processElement(in: HasdkLog, context: KeyedProcessFunction[String, HasdkLog, HasdkLog]#Context, collector: Collector[HasdkLog]): Unit = {
      var nowState = state.value()
      if (nowState == null) {
        nowState = MyState(in.user_id, transformTime(in.time_stamp))
        state.update(nowState)
        context.timerService.registerEventTimeTimer(nowState.time + 30000)
        collector.collect(in)
      } else {
        //        println("[Duplicate Data] " + in.user_id + " " + in.time_stamp)
      }

    }

    override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, HasdkLog, HasdkLog]#OnTimerContext, out: Collector[HasdkLog]): Unit = {
      val now = state.value()
      val nowDay = new Date(timestamp)
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      val nowStr = format.format(nowDay)
      val stateStr = format.format(now.time)
      if (now.time + 30000 <= timestamp) { //状态时间+30s <= 注册的定时器时间 进行清除
        //        println(s"[Overdue] 定时器时间nowTime: $timestamp->$nowStr 状态时间state_time:${now.time}-> $stateStr stateId: ${now.user_id}")
        state.clear()
      }

    }
  }

  case class MyState(user_id: String, time: Long)

  class CoProcessJoin extends CoProcessFunction[HasdkLog, HasdkLog, HasdkLog] {
    private var descriptor: ValueStateDescriptor[HasdkLog] = null
    private var showState: ValueState[HasdkLog] = null
    val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS")

    override def open(parameters: Configuration): Unit = {
      descriptor = new ValueStateDescriptor[HasdkLog]("hasdk_sample", classOf[HasdkLog])
      descriptor.enableTimeToLive(
        StateTtlConfig.newBuilder(org.apache.flink.api.common.time.Time.seconds(30))
          .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
          .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
          .disableCleanupInBackground()
          .build)
      showState = getRuntimeContext.getState(descriptor)
      println("open")
      //      println("open->"+showState.value())

    }


    //曝光
    override def processElement1(show: HasdkLog, context: CoProcessFunction[HasdkLog, HasdkLog, HasdkLog]#Context, collector: Collector[HasdkLog]): Unit = {
      if (show != null) {
        //更新状态并注册延迟时间
        showState.update(show)
        val l = System.currentTimeMillis()
        println(s"状态进入 ${show.user_id}  时间为: ${format.format(l)}")
        context.timerService.registerEventTimeTimer(transformTime(show.time_stamp) + 30 * 1000L)
      }
    }

    override def processElement2(click: HasdkLog, context: CoProcessFunction[HasdkLog, HasdkLog, HasdkLog]#Context, collector: Collector[HasdkLog]): Unit = {
      //若为正样本则去除该曝光样本的状态为null
      val nowstate = showState.value()
      val l = System.currentTimeMillis()
      //      println(s"当前${click.user_id}状态为 $nowstate   时间为: ${format.format(l)}")
      if (nowstate != null) {
        showState = null
      }
    }

    override def onTimer(timestamp: Long, ctx: CoProcessFunction[HasdkLog, HasdkLog, HasdkLog]#OnTimerContext, out: Collector[HasdkLog]): Unit = {

      if (showState != null) {
        //如果到了过期时间还不为null就是负样本
        out.collect(showState.value())
      }
    }
  }

  //点击流直接去重就行
  //曝光流nuion点击流开窗聚合去点击后,再去重(窗口间去重),然后再与点击流connect去除窗口之间的点击
}
