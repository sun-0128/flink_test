package com.guigu.flink.utils

import org.apache.flink.api.java.utils.ParameterTool

object ParametersConfig {
  //运行相关参数
  val Delimiter = "\u0001"
  val DELAT_TIME_MINUTES = 30
  var BOOTSTRAP_SERVERS = ""
  var AUTO_OFFSET_RESET = "latest"
  var GROUP_ID = ""
  var KEY_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer"
  var KEY_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer"
  var HASDK_TOPIC = "hasdk_log"
  var JSSDK_TOPIC = "jssdk_log"
  var PARALLELISM = 1
  var DATABASE = "seadads"
  var CATLOG_NAME = "hive"
  var HIVE_CONF_DIR = ""
  var HIVE_VERSION = ""
  var ASSEMBLE_CONFIG_PATH = ""
  var REDIS_PROP_PATH = ""
  var RAW_TABLE = ""
  var CHECKPOINT_INTERVAL = 5
  var PARTITION_COLUMNS = Array("pt_d")
  var DATA_PATH = ""



  /**
   * 初始化
   */
  def initConfig(configname: ParameterTool): Unit = {
    BOOTSTRAP_SERVERS = configname.get("bootstrap.servers")
    AUTO_OFFSET_RESET = configname.get("auto.offset.reset")
    HASDK_TOPIC = configname.get("hasdk_topic")
    JSSDK_TOPIC = configname.get("jssdk_topic")
    GROUP_ID = configname.get("group.id")
    PARALLELISM = configname.getInt("parallelism")
    DATABASE=configname.get("database")
    CATLOG_NAME=configname.get("catalogName")
    HIVE_CONF_DIR=configname.get("hive.config.dir")
    HIVE_VERSION=configname.get("hive.version")
    ASSEMBLE_CONFIG_PATH=configname.get("assemble.config.path")
    REDIS_PROP_PATH=configname.get("redisProp")
    System.setProperty("redis.prop",REDIS_PROP_PATH)
    RAW_TABLE=configname.get("rawTable")
    PARTITION_COLUMNS=configname.get("hive.partition.columns").split(",")
    CHECKPOINT_INTERVAL=configname.getInt("checkpoint.interval")
    DATA_PATH=configname.get("datapath")
  }
}

