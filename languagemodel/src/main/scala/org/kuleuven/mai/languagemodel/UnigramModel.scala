package org.kuleuven.mai.languagemodel

import breeze.linalg.DenseVector
import org.apache.log4j.Logger
import org.apache.spark.rdd.RDD
import org.kuleuven.mai.glove.GloveModel


/**
 * @author @mandar2812
 */
class UnigramModel[K](documentModel: RDD[(K, Map[String, Double])])
  extends Serializable {

  private val logger = Logger.getLogger(this.getClass)

  private var cache: Map[K, (Map[String, Double], DenseVector[Double])] = Map()

  def query(qwordfreq: Map[String, Double], globalModel: Map[String, Double],
            N: Int = 15, LAMBDA: Double = 0.5): List[(K, Double)] = {


    //Calculate the likelihood with respect to
    //all the documents in the collection
    val sc = documentModel.sparkContext
    val globalModelB = sc.broadcast(globalModel)
    val qwordfreqB =  sc.broadcast(qwordfreq.map(identity))
    //Calculate the likelihood estimator
    //for the global model
    val globalTermProbB = sc.broadcast(qwordfreq.map((couple) => {
      val prob = UnigramModel.getGlobalProb(globalModelB.value, couple._1)
      (couple._1, prob)
    }))

    val lambda = sc.broadcast(LAMBDA)

    documentModel.map((document) => {
      //For each document
      //Calculate the likelihood of the query
      //with respect to the document model
      val docTermProb = document._2
      val documentLikelihood = UnigramModel.qlikelihood(qwordfreqB.value,
        docTermProb, globalTermProbB.value, lambda.value)

      (document._1, documentLikelihood)
    }).sortBy(_._2, ascending = false)
      .take(N*4).toList
  }

  def queryWithGlove(qwordfreq: Map[String, Double], gloveModel: GloveModel,
                     globalModel: Map[String, Double], N: Int = 15,
                     LAMBDA: Double = 0.0): List[(K, Double)] = {


    if(cache.isEmpty) {
      cache = documentModel.collect().toList.map((document) => {
        val docTermProb = document._2
        val docVec = docTermProb.map{(p) => {
          val res:DenseVector[Double] = if(gloveModel.contains(p._1)) gloveModel.wordvector(p._1)(p._1)*p._2
          else DenseVector.zeros[Double](gloveModel.dimensions)
          res
        }
        }.toList
          .reduce(_+_)
        (document._1, (document._2, docVec))
      }).toMap
    }

    val sc = documentModel.sparkContext
    val qwordvec =  sc.broadcast(qwordfreq.map((p) => {
      val res:DenseVector[Double] =
        if(gloveModel.contains(p._1)) gloveModel.wordvector(p._1)(p._1)*p._2
        else DenseVector.zeros[Double](gloveModel.dimensions)
      res
    }).toList
      .reduce(_+_)
    )

    cache.map((document) => {
      val docTermProb = document._2._1
      val docVec = document._2._2
      val cosine = GloveModel.cosine(qwordvec.value, docVec)


      val globalTermProb = qwordfreq.map((couple) => {
        val prob = UnigramModel.getGlobalProb(globalModel, couple._1)
        (couple._1, prob)
      })

      val documentLikelihood = UnigramModel.qlikelihood(qwordfreq,
        docTermProb, globalTermProb, 0.5)
      val res = LAMBDA*documentLikelihood + (1-LAMBDA)*cosine
      (document._1, res)
    }).toList.sortBy(_._2)
      .reverse.take(N*4)
  }

}

object UnigramModel extends Serializable {

  val getWordFreq: String => Map[String, Double] = _.split('.').map{ substrings =>

    // Trim substrings and then tokenize on spaces
    substrings.trim.split(' ').

      // Remove non-alphanumeric characters and convert to lowercase
      map{_.replaceAll("""\W""", "").toLowerCase()}

  }.flatMap{a => a.toList}.
    // Group the unigrams and count their frequency
    groupBy{identity}.mapValues{_.size}

  def apply[K](trainingDocs: RDD[(K, String)]): UnigramModel[K] = {
    val docmodel = trainingDocs.map(p => {
      val freq = UnigramModel.getWordFreq(p._2)
      val normalizer = freq.map{_._2}.sum
      (p._1, freq.map{w => (w._1, w._2/normalizer)})
    })
    new UnigramModel[K](docmodel)
  }

  def evaluate[K](eq: (K, K) => Boolean)
                 (getId: (K) => String)
                 (choose: (List[Double]) => Double)
                 (qres: List[(K, Double)],
                  target: K): (Double, Double) = {
    var rr: Double = 0.0
    val groupedRes = qres.map(p => (getId(p._1), p._2))
      .groupBy(_._1).map((p) => (p._1, p._2.map{_._2}))

    val sortedRes = groupedRes.map{p => (p._1, choose(p._2))}
      .toList.sortBy(_._2)

    (0 to sortedRes.length - 1).foreach((p) => {
      if(getId(target) == sortedRes(p)._1) {
        rr = 1/(p.toDouble + 1)
      }
    })

    (math.floor(rr), rr)
  }

  def qlikelihood(qwordfreq: Map[String, Double],
                 docTermProb: Map[String, Double],
                 globalTermProb: Map[String, Double],
                 L: Double) = qwordfreq.map((couple) => {
    val prob = if(docTermProb.contains(couple._1))
      docTermProb(couple._1) else 0.0
    //Do Linear Interpolation Smoothing
    val smoothProb = L*prob + (1-L)*globalTermProb(couple._1)
    math.pow(smoothProb, couple._2)
  }).product

  def globalModelHas(global: Map[String, Double], q: String): Boolean = global.contains(q)

  def getGlobalProb(global: Map[String, Double], q: String): Double = if(globalModelHas(global, q)) global(q)
  else 0.0
}