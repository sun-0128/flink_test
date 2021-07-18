package com.guigu.flink


//import com.guigu.flink.KafkaData2Hdfs.transformTime
import org.apache.flink.orc.vector.Vectorizer
import java.sql.Timestamp

import com.guigu.flink.TestParameterTools.transformTime
import org.apache.flink.types.Row

import scala.collection.mutable

case class HasdkLog(
                     var user_id: String,
                     var event_id: String,
                     var invited: String,
                     var time_stamp: String,
                     var interested: String,
                     var timeStamp_flag: Timestamp
                   ) extends GetTime {


  override def toString: String = {
    val log =
      s"""
         |$user_id\001$event_id\001$invited\001$time_stamp\001$interested
         |""".stripMargin
    log
  }

  override def getTimeStamp(): Long = {
    transformTime(this.time_stamp)
  }
}

object HasdkLog {
  def naFill(log: HasdkLog) = {
    if (log.user_id == null) log.user_id = ""
    if (log.event_id == null) log.event_id = ""
    if (log.invited == null) log.invited = ""
    if (log.time_stamp == null) log.time_stamp = ""
    if (log.interested == null) log.interested = ""
    log
  }



  def map2RowData(map: mutable.Map[String, String]): Row = {
    val row = new Row(map.size)
    var a = 0
    map.foreach(x => {
      row.setField(a, x._2)
      a += 1
    })
    row
  }

}



