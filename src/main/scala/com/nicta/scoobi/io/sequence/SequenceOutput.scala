/**
 * Copyright 2011,2012 National ICT Australia Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nicta.scoobi
package io
package sequence

import org.apache.commons.logging.LogFactory
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Writable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.FileAlreadyExistsException
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.Job

import core._
import impl.ScoobiConfiguration._
import impl.io.Helper
import avro.AvroInput
import sequence.SequenceInput.SeqSource
import org.apache.hadoop.conf.Configuration
import impl.{ScoobiConfigurationImpl, ScoobiConfiguration}
import core.ScoobiConfiguration
import org.apache.hadoop.io.compress.CompressionCodec
import org.apache.hadoop.io.SequenceFile.CompressionType

/** Smart functions for persisting distributed lists by storing them as Sequence files. */
object SequenceOutput {

  /** Specify a distributed list to be persistent by converting its elements to Writables and storing it
    * to disk as the "key" component in a Sequence File. */
  def keyToSequenceFile[K](dl: DList[K], path: String, overwrite: Boolean = false)(implicit convK: SeqSchema[K]) =
    dl.addSink(keySchemaSequenceFile(path, overwrite))

  def keySchemaSequenceFile[K](path: String, overwrite: Boolean = false)(implicit convK: SeqSchema[K]) = {

    val keyClass = convK.mf.runtimeClass.asInstanceOf[Class[convK.SeqType]]
    val valueClass = classOf[NullWritable]

    val converter = new InputOutputConverter[convK.SeqType, NullWritable, K] {
      def fromKeyValue(context: InputContext, k: convK.SeqType, v: NullWritable) = convK.fromWritable(k)
      def toKeyValue(kv: K)(implicit configuration: Configuration) = kv match {
        case (k1, _) => (convK.toWritable(k1.asInstanceOf[K]), NullWritable.get)
        case k1      => (convK.toWritable(k1.asInstanceOf[K]), NullWritable.get)
      }
    }
    new SeqSink[convK.SeqType, NullWritable, K](path, keyClass, valueClass, converter, overwrite)
  }

  /** Specify a distributed list to be persistent by converting its elements to Writables and storing it
    * to disk as the "value" component in a Sequence File. */
  def valueToSequenceFile[V](dl: DList[V], path: String, overwrite: Boolean = false)(implicit convV: SeqSchema[V]) =
    dl.addSink(valueSchemaSequenceFile(path, overwrite))

  def valueSchemaSequenceFile[V](path: String, overwrite: Boolean = false)(implicit convV: SeqSchema[V]) = {
    val keyClass = classOf[NullWritable]
    val valueClass = convV.mf.runtimeClass.asInstanceOf[Class[convV.SeqType]]

    val converter = new InputOutputConverter[NullWritable, convV.SeqType, V] {
      def fromKeyValue(context: InputContext, k: NullWritable, v: convV.SeqType) = convV.fromWritable(v)
      def toKeyValue(kv: V)(implicit configuration: Configuration) = kv match {
        case (_,v) => (NullWritable.get, convV.toWritable(v.asInstanceOf[V]))
        case v     => (NullWritable.get, convV.toWritable(v.asInstanceOf[V]))
      }
    }
    new SeqSink[NullWritable, convV.SeqType, V](path, keyClass, valueClass, converter, overwrite)
  }

  /** Specify a distributed list to be persistent by converting its elements to Writables and storing it
    * to disk as "key-values" in a Sequence File. */
  def toSequenceFile[K, V](dl: DList[(K, V)], path: String, overwrite: Boolean = false, checkpoint: Boolean = false)(implicit convK: SeqSchema[K], convV: SeqSchema[V], sc: ScoobiConfiguration) =
    dl.addSink(schemaSequenceSink(path, overwrite, checkpoint)(convK, convV, sc))

  def schemaSequenceSink[K, V](path: String, overwrite: Boolean = false, checkpoint: Boolean = false)(implicit convK: SeqSchema[K], convV: SeqSchema[V], sc: ScoobiConfiguration)= {

    val keyClass = convK.mf.runtimeClass.asInstanceOf[Class[convK.SeqType]]
    val valueClass = convV.mf.runtimeClass.asInstanceOf[Class[convV.SeqType]]

    val converter = new InputOutputConverter[convK.SeqType, convV.SeqType, (K, V)] {
      def fromKeyValue(context: InputContext, k: convK.SeqType, v: convV.SeqType) = (convK.fromWritable(k), convV.fromWritable(v))
      def toKeyValue(kv: (K, V))(implicit configuration: Configuration) = (convK.toWritable(kv._1), convV.toWritable(kv._2))
    }
    new SeqSink[convK.SeqType, convV.SeqType, (K, V)](path, keyClass, valueClass, converter, overwrite, Checkpoint.create(Some(path), checkpoint)) with SinkSource {
      def toSource = new SeqSource(Seq(path), converter)
    }
  }

  def sequenceSink[K <: Writable, V <: Writable](path: String, overwrite: Boolean = false, checkpoint: Boolean = false)(
      implicit mk: Manifest[K], mv: Manifest[V], sc: ScoobiConfiguration) = {
    val keyClass = mk.runtimeClass.asInstanceOf[Class[K]]
    val valueClass = mv.runtimeClass.asInstanceOf[Class[V]]

    val converter = new InputOutputConverter[K, V, (K, V)] {
      def fromKeyValue(context: InputContext, k: K, v: V) = (k, v)
      def toKeyValue(kv: (K, V))(implicit configuration: Configuration) = (kv._1, kv._2)
    }
    new SeqSink[K, V, (K, V)](path, keyClass, valueClass, converter, overwrite, Checkpoint.create(Some(path), checkpoint)) with SinkSource {
      def toSource = new SeqSource(Seq(path), converter)
    }
  }

}

/** class that abstracts all the common functionality of persisting to sequence files. */
case class SeqSink[K, V, B](path: String,
                            keyClass: Class[K],
                            valueClass: Class[V],
                            outputConverter: InputOutputConverter[K, V, B],
                            overwrite: Boolean,
                            checkpoint: Option[Checkpoint] = None,
                            compression: Option[Compression] = None) extends DataSink[K, V, B] {

  private lazy val logger = LogFactory.getLog("scoobi.SequenceOutput")

  protected val output = new Path(path)

  def outputFormat(implicit sc: ScoobiConfiguration) = classOf[SequenceFileOutputFormat[K, V]]
  def outputKeyClass(implicit sc: ScoobiConfiguration) = keyClass
  def outputValueClass(implicit sc: ScoobiConfiguration) = valueClass

  def outputCheck(implicit sc: ScoobiConfiguration) {
    if (Helper.pathExists(output)(sc) && !overwrite) {
      throw new FileAlreadyExistsException("Output path already exists: " + output)
    } else logger.info("Output path: " + output.toUri.toASCIIString)
  }
  def outputPath(implicit sc: ScoobiConfiguration) = Some(output)

  def outputConfigure(job: Job)(implicit sc: ScoobiConfiguration) {}

  override def outputSetup(implicit configuration: Configuration) {
    if (Helper.pathExists(output)(configuration) && overwrite) {
      logger.info("Deleting the pre-existing output path: " + output.toUri.toASCIIString)
      Helper.deletePath(output)(configuration)
    }
  }

  def compressWith(codec: CompressionCodec, compressionType: CompressionType = CompressionType.BLOCK) = copy(compression = Some(Compression(codec, compressionType)))

  override def toString = getClass.getSimpleName+": "+outputPath(new ScoobiConfigurationImpl).getOrElse("none")
}
