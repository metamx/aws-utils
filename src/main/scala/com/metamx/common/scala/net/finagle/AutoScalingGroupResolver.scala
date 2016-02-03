package com.metamx.common.scala.net.finagle

import java.net.{InetSocketAddress, SocketAddress}
import java.util.concurrent.TimeUnit._
import java.util.concurrent.{ConcurrentHashMap, Executors}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScalingAsyncClient
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.AmazonEC2AsyncClient
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.metamx.common.concurrent.RepeatingThread
import com.metamx.common.lifecycle.Lifecycle
import com.metamx.common.scala.Logging
import com.twitter.finagle.{Addr, Resolver}
import com.twitter.util._
import org.scala_tools.time.Imports.Duration
import org.skife.config.{Config, Default}

import scala.collection.JavaConversions._
import scala.collection.convert.decorateAsScala._
import scala.util.Try

/**
 * Bridges Finagle with Auto Scaling Group <br/><br/>
 * It follows the following schema
 * <pre>asg://&lt;auto-scaling-group&gt;:&lt;port&gt;/&lt;path&gt;</pre>
 *
 * @param config config
 */
class AutoScalingGroupResolver(config: AsgResolverConfig) extends Resolver with Logging {
  override val scheme = "asg"

  val registry = new ASGRegistry(config)

  def bind(groupAndPort: String) = Var.async[Addr](Addr.Pending) {
    updatable =>
      val lifecycle = new Lifecycle
      val Array(group, portStr) = groupAndPort.split(":")
      val port = Try(portStr.toInt).toOption.getOrElse(8080)

      def doUpdate() {
        val IPs = registry.lookup(group)
        log.info("Updating instances for asg[%s] to %s", group, IPs)
        val newSocketAddresses: Set[SocketAddress] = IPs.toSet map
          (instance => new InetSocketAddress(instance, port))
        updatable.update(Addr.Bound(newSocketAddresses))
      }
      lifecycle.start()
      try {
        doUpdate()
        new Closable {
          def close(deadline: Time) = Future {
            log.info("No longer monitoring auto-scaling-group[%s]", group)
            lifecycle.stop()
          }
        }
      }
      catch {
        case e: Exception =>
          log.warn(e, "Failed to bind to auto-scaling-group[%s]", group)
          lifecycle.stop()
          throw e
      }
  }
}

class ASGRegistry(config: AsgResolverConfig) {
  // TODO Use better cache with time-to-live configuration
  val cache = new ConcurrentHashMap[String, List[String]]().asScala

  val credentials = new BasicAWSCredentials(config.accessKey, config.secretKey)
  val asgClient = new AmazonAutoScalingAsyncClient(credentials)
  val ec2Client = new AmazonEC2AsyncClient(credentials)

  val executor = Executors.newSingleThreadExecutor()

  val thread = new RepeatingThread(config.updatePeriod, new Runnable {
    override def run() = {
      cacheUpdate(cache.keys)
    }
  })

  thread.start

  def lookup(group: String): List[String] = {
    if (!cache.containsKey(group)) {
      cacheUpdate(List(group))
    }
    return cache.get(group).getOrElse(List())
  }

  def cacheUpdate(groups: Iterable[String]) = {
    try {
      if (!groups.isEmpty) {
        val request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames()
        val asGroups = asgClient.describeAutoScalingGroupsAsync(request)
          .get(config.apiTimeout, SECONDS)
          .getAutoScalingGroups

        for (asg <- asGroups) {
          val instanceIds = asg.getInstances.asScala map (instance => instance.getInstanceId)

          // Get private IpAddresses for the instance Ids
          val ec2Req = new DescribeInstancesRequest().withInstanceIds(instanceIds)
          val instances = ec2Client.describeInstancesAsync(ec2Req)
            .get(config.apiTimeout, SECONDS)
            .getReservations
            .asScala map (_.getInstances)

          val IPs = instances.flatten map (_.getPrivateIpAddress)

          cache.put(asg.getAutoScalingGroupName, IPs.toList)
        }
      }
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
}

trait AsgResolverConfig {
  @Config(Array("aws.secretKey"))
  def secretKey: String

  @Config(Array("aws.accessKey"))
  def accessKey: String

  @Config(Array("aws.api.timeout.secs"))
  def apiTimeout: Int

  @Config(Array("asg.resolver.update.period"))
  @Default("PT60S")
  def updatePeriod: Duration
}
