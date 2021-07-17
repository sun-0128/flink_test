package com.guigu.flink.utils

import java.io.{File, FileInputStream}
import java.util.Properties

import redis.clients.jedis.Jedis

object RedisUtils {
  def getClient(file: File)={

    val prop = new Properties()
    prop.load(new FileInputStream(file))
    val host = prop.getProperty("host")
    val port = prop.getProperty("port")
    new Jedis(host, port.toInt)
  }
}
