package org.apache.spark


import org.apache.spark.util.Utils
import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.{SparkListener, SparkListenerApplicationEnd, SparkListenerApplicationStart}

/**
  * Created by cloud on 18/1/19.
  */
class StartSparkAppListener(val sparkConf: SparkConf) extends SparkListener with Logging{

  private val appName = sparkConf.get("spark.app.name")
  private val runConf = sparkConf.get("spark.run.main.conf")
  private val host = sparkConf.get("spark.application.monitor.host",Utils.localHostName)
  private val port = sparkConf.getInt("spark.application.monitor.port",23456)

  private def sendStartReq(): Unit = {
    val yarnAppMonitorRef = YarnAppMonitorCli.createYarnAppMonitorRef(sparkConf,host,port)
    yarnAppMonitorRef.send(YarnAppStartRequest(appName,runConf))
    logInfo(s"send start app request to YarnAppMonitorServer $appName $runConf")
  }

  override def onApplicationStart(applicationStart: SparkListenerApplicationStart): Unit = {
    val appId = applicationStart.appId
    logInfo(appId.toString)
  }

  override def onApplicationEnd(applicationEnd: SparkListenerApplicationEnd): Unit = {
    logInfo("app end time " + applicationEnd.time)
    sendStartReq()
  }

}