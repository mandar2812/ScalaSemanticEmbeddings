package org.kuleuven.mai.languagemodel

import java.io.File

import breeze.linalg.DenseVector
import com.github.tototoshi.csv.CSVWriter
import org.apache.log4j.Logger
import org.apache.spark.{SparkContext, SparkConf}
import org.kuleuven.mai.glove.GloveModel
import org.kuleuven.mai.utils

import scala.util.Random

/**
 * @author @mandar2812
 */
object FlikrTestUnigram extends Serializable{
  def main(args: Array[String]): Unit = {
    val logger = Logger.getLogger(this.getClass)

    val root = args(0) match {
      case "local" => "file://"+args(1)
      case "yarn" => "hdfs://"+args(1)
    }

    val conf = new SparkConf().setAppName("Flickr8kUnigram").setMaster("local[4]")
    val sc = new SparkContext(conf)

    val writer = args(2) match {
      case "unigram" =>
        CSVWriter.open(new File("/var/Datasets/textBasedIR/flickr8k_unigram_results.csv"),
          append = true)
      case "hybrid" =>
        CSVWriter.open(new File("/var/Datasets/textBasedIR/flickr8k_unigram_hybrid_results.csv"),
          append = true)
    }

    //Load the Flikr image annotations
    //and the list of test images
    logger.info("Loading test image IDs and captions")
    val imageIds = sc.textFile(root+"Flicker8k_annotations/Flickr_8k.testImages.txt").collect()
    val annotations = sc.textFile(root+
      "Flicker8k_annotations/Flickr8k.lemma.token.txt")
      .map((line) => {
      val line_split = line.split('\t')
      val id_split = line_split(0).split('#')
      val sentence = line_split(1)
      ((id_split(0), id_split(1).toInt), sentence)
    }).filter((p) => imageIds contains p._1._1)
      .cache()
    logger.info("*************")
    logger.info("Annotations: "+annotations.count())
    logger.info("*************")
    logger.info("Calculating global Language model")
    //For Linear Interpolation Smoothing
    //we must first generate a global unigram
    //language model. For that we must
    //collate all the sentences together
    //and generate word frequencies
    val sentences = annotations.map{p => p._2}
    val unigram_freq_global = sentences.map{
      UnigramModel.getWordFreq
    }.flatMap{identity}.reduceByKey(_+_).collect

    val normalizer:Double = unigram_freq_global.map{_._2}.sum
    val unigram_prob_global = unigram_freq_global.map{w =>
      (w._1, w._2.toDouble/normalizer)}.toMap
    logger.info("*************")
    logger.info("Global Frequencies: "+ unigram_prob_global)
    logger.info("*************")

    logger.info("Global Model calculated, now proceeding to splitting data set")

    //Now separate out the training
    //sentences from the test queries
    val testIDs = imageIds.map((_, Random.nextInt(5))).toList
    logger.info("*************")
    logger.info("Test Ids count: "+ testIDs)
    logger.info("*************")
    val testset = annotations.filter(i => testIDs.contains(i._1))
      .collect()
      .toList
    logger.info("*************")
    logger.info("Test Set Sample: "+ testset.take(5))
    logger.info("*************")
    logger.info("Test Set count: "+ testset.length)
    logger.info("*************")

    logger.info("Test queries separated, now getting document models")
    val trainingSet = annotations.filter((i) => {
      !testIDs.contains(i._1)
    }).cache()
    logger.info("*************")
    logger.info("Training Set count: "+ trainingSet.count())
    logger.info("*************")
    annotations.unpersist()
    val tf = CSVWriter.open(new File("/var/Datasets/textBasedIR/flickr_temp.csv"),
      append = true)

    val fList: Map[String, List[Double] => Double] =
      Map("mean" -> {(p) => p.sum/p.length},
        "max" -> {(p) => p.max},
        "median" -> {(p) => utils.median(p)})

    val unigramModel = UnigramModel(trainingSet)

    val gloveModel = args(2) match {
      case "hybrid" =>
        val glove =
          sc.textFile(root+"vectors.6B.50d.txt",
          minPartitions = 4).map((line) => {
          val splits = line.split(' ')
          (splits(0),
            DenseVector(splits.slice(1, splits.length)
              .map((num: String) => num.toDouble))
            )
        }).cache()
        new GloveModel(glove)
    }

    val imageEq = (a: (String, Int), b:(String, Int)) => a._1 == b._1
    val lambdavals = List(0.05, 0.2, 0.4, 0.6, 0.8, 0.95)
    fList.foreach((func) => {
      lambdavals.foreach((l) => {
        val metrics = testset.map((query) => {
          val recs = args(2) match {
            case "unigram" =>
              unigramModel.query(UnigramModel.getWordFreq(query._2),
                unigram_prob_global, LAMBDA = l)
            case "hybrid" =>
              unigramModel.queryWithGlove(UnigramModel.getWordFreq(query._2),
                gloveModel, unigram_prob_global, LAMBDA = l)
          }

          val (recall, rr) = UnigramModel
            .evaluate(imageEq)(_._1)(func._2)(recs, query._1)
          logger.info("Recall@1: "+recall+" MRR: "+rr)
          tf.writeRow(List(func._1, l, recall, rr))
          (recall, rr)
        })

        val res = metrics.reduce((a, b) => (a._1+b._1, a._2+b._2))
        logger.info("Metrics: Recall@1: " +
          res._1/metrics.length +
          " MRR: " + res._2/metrics.length)
        writer.writeRow(List(func._1, l, res._1/metrics.length, res._2/metrics.length))
      })

    })
    sc.stop()
    writer.close()
    tf.close()
  }
}
