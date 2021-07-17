//package com.guigu.flink
//
//import java.util
//
//import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
//import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
//import org.apache.spark.SparkConf
//import org.apache.spark.sql.SparkSession
//import org.apache.spark.streaming.dstream.{DStream, InputDStream}
//import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
//import org.apache.spark.streaming.{Seconds, StreamingContext}
//
//
//object Spark2Kafka {
//  def main(args: Array[String]): Unit = {
//    //TODO 创建streamcontext
//    val kafkaParams = Map(
//      (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "hadoop01:9092"),
//      (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer"),
//      (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer"),
//      (ConsumerConfig.GROUP_ID_CONFIG -> "hasdk_log_test01")
//    )
//    val conf = new SparkConf().setAppName("testkafka").setMaster("local[*]")
//    val spark = SparkSession.builder().config(conf).enableHiveSupport().getOrCreate()
//    import spark.implicits._
//    val frame = spark.table("events.train")
//    val rdd = frame.toJSON
//    val jsonStr = rdd.collect()
//
//
//    jsonStr.map(x =>
//      x + "\u0001abcd" + "\u0001abcd"
//    ).
//      foreach(json => {
//        val props = new util.HashMap[String, Object]()
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "hadoop01:9092")
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
//          "org.apache.kafka.common.serialization.StringSerializer")
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
//          "org.apache.kafka.common.serialization.StringSerializer")
//        println(json)
//        val producer = new KafkaProducer[String, String](props)
//        val message = new ProducerRecord[String, String]("hasdk_log_test", null, json)
//        producer.send(message)
//
//      })
//
//    //    val ssc = new StreamingContext(spark.sparkContext,Seconds(1))
//    //    val message: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
//    //      ssc, LocationStrategies.PreferConsistent, ConsumerStrategies
//    //      .Subscribe(Set("hasdk_log_test"), kafkaParams))
//    //    val value: DStream[(String)] = message.map(x=>x.value())
//    //    value.print()
//    //    ssc.start()
//    //    ssc.awaitTermination()
//  }
//}
