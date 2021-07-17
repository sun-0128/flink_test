package com.guigu.flink

import java.sql.Timestamp

import com.guigu.flink.KafkaData2Hdfs.transformTime
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.hive.HiveCatalog
import redis.clients.jedis.Jedis

import scala.collection.mutable.ArrayBuffer

object TestBatchEnv {
  def main(args: Array[String]): Unit = {
    val env = ExecutionEnvironment.getExecutionEnvironment
    val settings = EnvironmentSettings.newInstance().useBlinkPlanner().inBatchMode().build()
    val tblBatchEnv = TableEnvironment.create(settings)
    val catalog = new HiveCatalog("hive", "events", "G:\\code\\flink_test\\src\\main\\resources", "1.1.0")
    import org.apache.flink.table.api.SqlDialect
    tblBatchEnv.registerCatalog("hive", catalog)
    tblBatchEnv.useCatalog("hive")
    val result = tblBatchEnv.executeSql(
      """
        |select * from train_test_flink where pt_d = '20121002'
        |""".stripMargin)

    val names = result.getTableSchema.getFieldNames
    println(names.mkString(","))
    println("get columns end")
    val value = result.collect()
    val logs = new ArrayBuffer[HasdkLog]()
    val strings = new ArrayBuffer[String]()
    while (value.hasNext) {
      val row = value.next()
      val value1 = row.getField(0).toString
      val value2 = row.getField(1).toString
      val value3 = row.getField(2).toString
      val value4 = row.getField(3).toString
      val value5 = row.getField(4).toString
      val arity = row.getArity
      strings.append(row.toString)
      logs.append(HasdkLog(value1, value2, value3, value4, value5, new Timestamp(transformTime(value4))))
    }
    val array = logs.toArray
    strings.foreach(println(_))
    //hmset myhash field1 10 field2 20
    val jedis = new Jedis("hadoop01", 6379)
    val str = jedis.hget("myhash", "field1")
    println(str)
    println(jedis.hget("3044014", "field3") == null)

    val value1 = env.readTextFile("hdfs://hadoop01:9000/data/train_test_flink/pt_d=20121107/part-48bfc049-f3df-4d15-8d39-437c8dcbefd0-0-0")
    value1.collect().foreach(println(_))


  }
}
