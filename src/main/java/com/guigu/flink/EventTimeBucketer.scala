package com.guigu.flink

import org.apache.flink.streaming.connectors.fs.Clock
import org.apache.hadoop.fs.Path
import java.io.IOException
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

import org.apache.flink.streaming.connectors.fs.bucketing.Bucketer


class EventTimeBucketer[T <: GetTime] extends Bucketer[T] {

  private var formatString: String = "yyyyMMdd"
  private var zoneId: ZoneId = ZoneId.systemDefault()
  @transient private var dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault())


  def this(formatString: String, zoneId: ZoneId) = {
    this()
    this.formatString = formatString
    this.zoneId = zoneId
    this.dateTimeFormatter = DateTimeFormatter.ofPattern(this.formatString).withZone(this.zoneId)

  }

  def this(formatString: String) = {
    this(formatString, ZoneId.systemDefault())
  }

  def this(zoneId: ZoneId) = {
    this("yyyyMMdd", zoneId)
  }

  @throws[IOException]
  @throws[ClassNotFoundException]
  def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    this.dateTimeFormatter = DateTimeFormatter.ofPattern(formatString).withZone(zoneId)
  }


  override def getBucketPath(clock: Clock, basePath: Path, element: T): Path = {
    val format = new SimpleDateFormat(formatString)
    var newDateTimeString = format.format(new Date(element.getTimeStamp()))
    newDateTimeString = s"pt_d=$newDateTimeString"
    new Path(basePath + "/" + newDateTimeString)
  }
}