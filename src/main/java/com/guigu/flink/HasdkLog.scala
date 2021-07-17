package com.guigu.flink

import org.apache.flink.table.data.StringData
import com.guigu.flink.KafkaData2Hdfs.transformTime
import org.apache.flink.orc.vector.Vectorizer
import org.apache.flink.table.data.GenericRowData
import org.apache.flink.table.data.RowData
import java.sql.Timestamp

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

  def toRawData(log: HasdkLog): RowData = {
    val rowData = new GenericRowData(5)
    rowData.setField(0, StringData.fromString(log.user_id))
    rowData.setField(1, StringData.fromString(log.event_id))
    rowData.setField(2, StringData.fromString(log.invited))
    rowData.setField(3, StringData.fromString(log.time_stamp))
    rowData.setField(4, StringData.fromString(log.interested))
    rowData
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



