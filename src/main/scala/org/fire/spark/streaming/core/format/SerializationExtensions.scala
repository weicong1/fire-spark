package org.fire.spark.streaming.core.format

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.mapred.AvroKey
import org.apache.avro.mapreduce.{AvroJob, AvroKeyInputFormat, AvroKeyOutputFormat}
import org.apache.avro.specific.SpecificData
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapreduce.Job
import org.apache.log4j.Logger
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.fire.spark.streaming.core.format.avro.MultipleAvroOutputsFormat

import scala.reflect.{ClassTag, classTag}

object SerializationExtensions {

    private val logger = Logger.getLogger(getClass)

    /**
      * 构建一个 Avro Job
      *
      * @param job
      * @tparam T
      * @return
      */
    def avroJob[T <: GenericRecord : ClassTag](job: Job = Job.getInstance(new Configuration())): Job = {
        val schema: Schema = SpecificData.get.getSchema(classTag[T].runtimeClass)
        //    val schema: Schema = User.getClassSchema
        AvroJob.setInputKeySchema(job, schema)
        AvroJob.setOutputKeySchema(job, schema)
        job
    }

    /**
      * 判断字段是否定义
      *
      * @param record
      * @param field
      * @return
      */
    def definedOrLog(record: GenericRecord, field: String): Boolean = {
        if (record.get(field) != null) return true
        logger.warn(s"Expected field '$field' to be defined, but it was not on record of type '${record.getClass}'")
        false
    }


    /**
      * 隐士转换 AvroRDDSparkContext
      *
      * @param sparkContext
      */
    implicit class AvroRDDSparkContext(val sparkContext: SparkContext) extends AnyVal {

        /**
          * 读取指定路径下的 Avro 文件
          *
          * @param path
          * @tparam T
          * @return
          */
        def avroFile[T: ClassTag](path: String): RDD[T] = {
            sparkContext.newAPIHadoopFile(path,
                classOf[AvroKeyInputFormat[T]],
                classOf[AvroKey[T]],
                classOf[NullWritable],
                avroJob().getConfiguration)
                    .map[T](_._1.datum())
        }
    }

    /**
      * 隐士构建 AvroRDD
      *
      * @param avroRDD
      * @tparam T
      */
    implicit class AvroRDD[T <: GenericRecord : ClassTag](val avroRDD: RDD[T]) {
        /**
          * 过滤指定字段未定义的数据
          *
          * @param fields
          * @return
          */
        def filterIfUnexpectedNull(fields: String*): RDD[T] = {
            avroRDD.filter(r => fields.forall(definedOrLog(r, _)))
        }

        /**
          * 保存为 Avro 文件
          *
          * @param outputPath 输出路径
          */
        def saveAsAvroFile(outputPath: String): Unit = {
            avroRDD.map(r => (new AvroKey[T](r), NullWritable.get))
                    .saveAsNewAPIHadoopFile(outputPath,
                        classOf[AvroKey[T]],
                        classOf[NullWritable],
                        classOf[AvroKeyOutputFormat[T]],
                        avroJob[T]().getConfiguration)
        }

        /**
          * 根据 Key 值保存指定文件名
          *
          * @param outputKeyFun
          * @param outputPath
          */
        def saveAsMultipleAvroFiles(outputKeyFun: (T) => String, outputPath: String): Unit = {
            avroRDD.map(r => ((outputKeyFun(r), new AvroKey(r)), NullWritable.get))
                    .saveAsNewAPIHadoopFile(outputPath,
                        classOf[AvroKey[(String, T)]],
                        classOf[NullWritable],
                        classOf[MultipleAvroOutputsFormat[T]],
                        avroJob[T]().getConfiguration)
        }
    }

}
