#! /usr/bin/Rscript
setwd("/var/textBasedIR/")
library(ggplot2)
df <- read.csv("flickr8k_unigram_hybrid_results.csv", 
               header = FALSE, 
               col.names = c("method", "lambda", 
                             "recall@1", "mrr"))

setwd("/home/mandar/Desktop/")
p1 <- ggplot(df, aes(x = recall.1, y = mrr)) +
  geom_point(size=3, aes(color=factor(method), shape=factor(method))) +
  geom_smooth(aes(color=factor(method)), method = "lm", se = FALSE) + 
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Recall@1", 
       y = "Mean Reciprocal Rank", 
       title= "Linear Regression (95% CI) of Mean Reciprocal Rank vs Recall@1, by selection method")

p2 <- ggplot(df, aes(x = lambda, y = mrr))+ 
  geom_point(size=3, aes(color=factor(method), shape=factor(method))) +
  geom_smooth(aes(color=factor(method)), method = "lm", se = FALSE) + 
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Number of Lambda", 
       y = "Mean Reciprocal Rank", 
       title= "Linear Regression (95% CI) of Mean Reciprocal Rank vs Lambda, by selection method")

p3 <- ggplot(df, aes(x = lambda, y = recall.1)) + 
  geom_point(size=3, aes(color=factor(method), shape=factor(method))) +
  geom_smooth(aes(color=factor(method)), method = "lm", se = FALSE) +
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Number of Lambda", 
       y = "Recall@1", 
       title= "Linear Regression (95% CI) of Recall@1 vs Lambda, by selection method")
