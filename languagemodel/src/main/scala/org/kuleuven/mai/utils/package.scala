package org.kuleuven.mai

import scala.util.Random

/**
 * @author mandar2812
 */
package object utils {
  def median(list: Seq[Double]): Double = {
    val random: (Int) => Int = Random.nextInt
    def medianK(list_sample: Seq[Double], k: Int, pivot: Double): Double = {
      val split_list = list_sample.partition(_ < pivot)
      val s = split_list._1.length
      if(s == k) {
        pivot
      } else if (list_sample.sum == pivot * list_sample.length) {
        pivot
      } else if(s < k) {
        medianK(split_list._2, k - s,
          split_list._2(random(split_list._2.length)))
      } else {
        medianK(split_list._1, k,
          split_list._1(random(split_list._1.length)))
      }
    }
    medianK(list, list.length/2, list(random(list.length)))
  }

}
