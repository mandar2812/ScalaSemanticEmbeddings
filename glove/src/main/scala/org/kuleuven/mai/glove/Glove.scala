package org.kuleuven.mai.glove

import java.io.File

import breeze.linalg.DenseVector
import com.github.tototoshi.csv.CSVWriter
import org.apache.log4j.Logger
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.{SparkConf, SparkContext}

/**
 * @author @mandar2812
 * */

object Glove {

  def main(args: Array[String]): Unit = {
    val logger = Logger.getLogger(this.getClass)
    val dims = List("50d", "100d", "200d", "300d")
      .filter((d) => (args(2).contains(d) && !args(2).contains("--except")) ||
      (!args(2).contains(d) && args(2).contains("--except")) ||
      args(2) == "all")

    val root = args(0) match {
      case "local" => "file://"+args(1)
      case "yarn" => "hdfs://"+args(1)
    }

    val conf = new SparkConf().setAppName("Glove").setMaster("local[4]")
    val sc = new SparkContext(conf)
    val results = List(List("dimensions", "analogy", "recall", "mrr"))

    val writer = CSVWriter.open(new File("/var/Datasets/textBasedIR/glove_results.csv"))

    val fileTest = "questions-words.txt"
    dims.foreach((dim) => {
      val data = sc.textFile(root+"vectors.6B."+dim+".txt", minPartitions = 4)
      val writerdim = CSVWriter.open(new File("/var/Datasets/textBasedIR/"+dim+".csv"))
      val glove = data.map((line) => {
        val splits = line.split(' ')
        (splits(0),
          DenseVector(splits.slice(1, splits.length)
            .map((num: String) => num.toDouble))
          )
      }).cache()

      val glovemodel = new GloveModel(glove)
      val analogies = List("capital-common-countries", "capital-world",
        "currency", "city-in-state", "family", "gram1-adjective-to-adverb",
        "gram2-opposite", "gram3-comparative", "gram4-superlative",
        "gram5-present-participle", "gram6-nationality-adjective",
        "gram7-past-tense", "gram8-plural", "gram9-plural-verbs")
        .filter((d) => (args(3).contains(d) && !args(3).contains("--except")) ||
        (!args(3).contains(d) && args(3).contains("--except")) ||
        args(3) == "all")

      val testFile = sc.textFile(root+fileTest, minPartitions = 4)

      //Evaluate the performance of the GloVe vectors of
      //dimension dim
      analogies.foreach((analogy) => {
        //For each analogy extract the relevant part of the test file
        //into a list
        var activated = false
        var begin = false

        val analogytest = testFile.filter((line) => {
          if(line.contains(analogy)) {
            activated = true
          } else if (line.contains(": ")) {
            activated = false
            begin = false
          } else if (activated) {
            begin = true
          }
          begin
        }).sample(withReplacement = false, 0.04)
          .map((line) => line.split(' ').toList match {
          case List(a, b, c, d) =>
            (a.toLowerCase,
            b.toLowerCase,
            c.toLowerCase,
            d.toLowerCase)
        }).collect().toList

        logger.info("For: "+analogy)

        val metrics = analogytest.map((words) => {
          logger.info("Analyzing: "+words._1+":"+words._2+":"+words._3+":"+words._4)
            val (recall, mrr) = glovemodel.analogy(words._1, words._2,
              words._3, words._4)
            logger.info("For "+words._1+":"+words._2+":"+words._3+":"+words._4+
              " Recall: "+math.floor(recall)+" MRR: "+mrr)
            writerdim.writeRow(List(dim.substring(0, 2).toInt,
              analogy, recall,
              mrr))
            (recall, mrr)
        }).reduce((a1, a2) =>
          (a1._1+a2._1, a1._2+a2._2))

        logger.info("For dim = "+dim.substring(0, 2)+" analogy: "+analogy+
          " Recall@1: "+metrics._1+
          " MRR: "+metrics._2)

        results :+ List(dim.substring(0, 2).toInt,
          analogy, metrics._1/analogytest.length,
          metrics._2/analogytest.length)

        writer.writeRow(List(dim.substring(0, 2).toInt,
          analogy, metrics._1/analogytest.length,
          metrics._2/analogytest.length))
      })

      writerdim.close()
    })

    sc.stop()
    writer.close()
    logger.info("Results:\n "+results)

  }

}
