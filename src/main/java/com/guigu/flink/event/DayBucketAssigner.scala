package com.guigu.flink.event

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import com.guigu.flink.GetTime
import org.apache.flink.core.io.SimpleVersionedSerializer
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner


class DayBucketAssigner[ObjectNode <: GetTime] extends BucketAssigner[ObjectNode, String] {

  /**
   * bucketId is the output path
   *
   * @param element 输入元素
   * @param context 上下文
   * @return
   */
  override def getBucketId(element: ObjectNode, context: BucketAssigner.Context): String = {
    val format = new SimpleDateFormat("yyyyMMdd")
    var newDateTimeString = format.format(new Date(element.getTimeStamp()))
    newDateTimeString = s"pt_d=$newDateTimeString"
    newDateTimeString
  }


  override def getSerializer: SimpleVersionedSerializer[String] = {
    new SimpleVersionedSerializer[String] {
      override def getVersion: Int = 77

      @throws[IOException]
      override def serialize(e: String): Array[Byte] = e.getBytes(StandardCharsets.UTF_8)

      @throws[IOException]
      override def deserialize(version: Int, serialized: Array[Byte]): String =
        if (version != 77) {
          throw new IOException("version mismatch")
        }
        else {
          new String(serialized, StandardCharsets.UTF_8)
        }
    }
  }

}
