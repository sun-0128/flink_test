package com.guigu.flink



import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import com.alibaba.fastjson.JSON
import com.guigu.flink.KafkaData2Hdfs.transformTime
import com.guigu.flink.event.DayBucketAssigner
import org.apache.flink.streaming.connectors.fs.bucketing.DateTimeBucketer

object Test {
  def main(args: Array[String]): Unit = {
    println("ssss")
    val l = transformTime("2012-10-02 15:53:05.754000+00:00")
    println(l)
    val json =
      """
        |{"user_id":"3044012","event_id":"1918771225","invited":"0","time_stamp":"2012-10-02 15:53:05.754000+00:00","interested":"0"}
        |""".stripMargin


    val obj = JSON.parseObject(json)
    val str = obj.getString("A")
    println(str==null)
    println(str)
    val bucket = new DateTimeBucketer[HasdkLog]("yyyyMMdd")
    println(bucket)
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Shanghai"))

    val newDateTimeString = dateTimeFormatter.format(Instant.ofEpochMilli(l.toString.substring(0,10).toLong*1000))
    println(newDateTimeString)
//    val value = new DayBucketAssigner[HasdkLog]()

  }


}
