//package com.guigu.flink.event
//
//import java.nio.ByteBuffer
//
//import com.guigu.flink.HasdkLog
//import org.apache.flink.orc.vector.Vectorizer
//import org.apache.orc.{TypeDescription, Writer}
//
//class HasdkVectorizer extends Vectorizer[HasdkLog] {
//  override def addUserMetadata(key: String, value: ByteBuffer): Unit = super.addUserMetadata(key, value)
//
//  override def getSchema: TypeDescription = super.getSchema
//
//
//  override def setWriter(writer: Writer): Unit = super.setWriter(writer)
//
//}
