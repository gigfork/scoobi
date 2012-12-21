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
package impl
package mapreducer

import org.apache.commons.logging.LogFactory
import org.apache.hadoop.mapreduce.{Mapper => HMapper}

import core._
import rtt._
import util.DistCache
import plan.mscr.{InputChannels, InputChannel}

/**
 * Hadoop Mapper class for an MSCR
 *
 * It is composed of several tagged mappers which are taking inputs of a given type on a channel and emitting the result
 * for different tagged outputs
 */
class MscrMapper[K1, V1, A, E, K2, V2] extends HMapper[K1, V1, TaggedKey, TaggedValue] {

  lazy val logger = LogFactory.getLog("scoobi.MapTask")
  private var inputChannels: InputChannels = _
  private var inputChannel: InputChannel = _
  private var tk: TaggedKey = _
  private var tv: TaggedValue = _

  override def setup(context: HMapper[K1, V1, TaggedKey, TaggedValue]#Context) {

    mappers = DistCache.pullObject[InputChannels](context.getConfiguration, "scoobi.mappers").getOrElse(InputChannels())
    tk = context.getMapOutputKeyClass.newInstance.asInstanceOf[TaggedKey]
    tv = context.getMapOutputValueClass.newInstance.asInstanceOf[TaggedValue]

    val inputSplit = context.getInputSplit.asInstanceOf[TaggedInputSplit]
    logger.info("Starting on " + java.net.InetAddress.getLocalHost.getHostName)
    logger.info("Input is " + inputSplit)
    inputChannel = inputChannels.channel(inputSplit.channel)
    inputChannel.setup()

  }

  override def map(key: K1, value: V1, context: HMapper[K1, V1, TaggedKey, TaggedValue]#Context) {
    inputChannel.map(key, value, context)
  }

  override def cleanup(context: HMapper[K1, V1, TaggedKey, TaggedValue]#Context) {
    inputChannel.cleanup(context)
  }
}