package org.kuleuven.mai.glove

import org.apache.log4j.Logger
import org.apache.spark.mllib.linalg.{Vectors, Vector}
import org.apache.spark.rdd.RDD
import breeze.linalg.{norm, DenseVector => BDV}

/**
 * @author @mandar2812
 */
class GloveModel(vectors: RDD[(String, BDV[Double])]) extends Serializable {
  private val vocabulary = vectors.keys.cache

  private val logger = Logger.getLogger(this.getClass)

  val dimensions = vectors.first()._2.length

  def contains(word: String): Boolean = vocabulary.collect().contains(word)

  def wordvector(word: String): Map[String, BDV[Double]] = {
    assert(contains(word), word+" not in vocabulary!")
    vectors.filter((vector) => vector._1 == word).collect.toMap
  }

  def wordvector(words: List[String]): Map[String, BDV[Double]] =
    vectors.filter((vector) => words contains vector._1).collect.toMap

  def findSynonyms(vector: BDV[Double], N: Int): List[(String, Double)] =
    vectors.map((vec) => (vec._1, GloveModel.cosine(vec._2, vector)))
      .sortBy((k) => k._2, ascending = false).take(N).toList

  def analogy(word1: String, word2: String,
              word3: String, word4: String,
              N: Int = 15): (Double, Double) = {
    val words = List(word1, word2, word3)

    var flag: Boolean = false
    var rr: Double = 0.0
    val vocab = vocabulary.filter((w) => w == word1 || w == word2 || w == word3).collect()
    if((vocab contains word1) && (vocab contains word2) && (vocab contains word3)) {
      val wordvectors = wordvector(words)
      val diff:BDV[Double] = wordvectors(word2) + wordvectors(word3)
      diff :-= wordvectors(word1)
      val synonyms = findSynonyms(diff, N)
      val ranks = (0 to N-1).filter((i) => {
        synonyms(i)._1 == word4
      })

      rr = if(ranks.nonEmpty) 1/(ranks.head.toDouble + 1) else 0.0
    } else {
      flag = true
      logger.info("Either "+word1+" or "+word2+" or "+word3+" is/are not in the dictionary!")
    }

    (math.floor(rr), rr)
  }

}

object GloveModel extends Serializable {
  def cosine(vec1: BDV[Double], vec2: BDV[Double]): Double = {
    vec1 :/= norm(vec1)
    vec2 :/= norm(vec2)
    vec1 dot vec2
   }

  def euclidean(vec1: BDV[Double], vec2: BDV[Double]): Double =
    norm(vec1 :-= vec2, 2)
 }