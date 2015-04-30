package org.kuleuven.mai.languagemodel

import java.io.File

import com.github.tototoshi.csv.CSVWriter
import org.apache.log4j.Logger
import org.apache.spark.{SparkContext, SparkConf}

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

    val writer = CSVWriter.open(new File("/var/Datasets/textBasedIR/flickr8k_unigram_results.csv"),
      append = true)

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

    val unigramModel = UnigramModel(trainingSet)

    val imageEq = (a: (String, Int), b:(String, Int)) => a._1 == b._1

    val metrics = testset.map((query) => {
      val recs = unigramModel.query(UnigramModel.getWordFreq(query._2), unigram_prob_global)

      val (recall, rr) = UnigramModel
        .evaluate(imageEq)(_._1)((p: List[Double]) => p.sum/p.length)(recs, query._1)
      logger.info("Recall@1: "+recall+" MRR: "+rr)
      writer.writeRow(List(recall, rr))
      (recall, rr)
    })

    val res = metrics.reduce((a, b) => (a._1+b._1, a._2+b._2))
    logger.info("Metrics: Recall@1: " +
      res._1/metrics.length +
      " MRR: " + res._2/metrics.length)
    sc.stop()
    writer.close()
  }
}
