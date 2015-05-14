#! /usr/bin/Rscript
setwd("/var/textBasedIR/")
df <- read.csv("glove_results.csv", 
           header = FALSE, 
           col.names = c("dimension", "category", 
                         "recall@1", "mrr"))

setwd("/home/mandar/Desktop/")
p1 <- ggplot(df, aes(x = recall.1, y = mrr)) +
  geom_point(size=4, aes(color=factor(category), shape=factor(category))) +
  geom_smooth(aes(color=factor(category)), method = "lm", se = FALSE) + 
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Recall@1", 
       y = "Mean Reciprocal Rank", 
       title= "Linear Regression (95% CI) of Mean Reciprocal Rank vs Recall@1, by Category")
ggsave("mrr_recall.jpeg", p1, width = 3.5, height = 3.5)

p2 <- ggplot(df, aes(x = dimension, y = mrr))+ 
  geom_point(size=4, aes(color=factor(category), shape=factor(category))) +
  geom_smooth(aes(color=factor(category)), method = "lm", se = FALSE) + 
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Number of Dimensions", 
       y = "Mean Reciprocal Rank", 
       title= "Linear Regression (95% CI) of Mean Reciprocal Rank vs Dimensions, by Category")
ggsave("mrr_dim.jpeg", p2, width = 3.5, height = 3.5)

p3 <- ggplot(df, aes(x = dimension, y = recall.1)) + 
  geom_point(size=4, aes(color=factor(category), shape=factor(category))) +
  geom_smooth(aes(color=factor(category)), method = "lm", se = FALSE) +
  theme_bw() + 
  theme(                              
    axis.title.x = element_text(face="bold", color="black", size=12),
    axis.title.y = element_text(face="bold", color="black", size=12),
    plot.title = element_text(face="bold", color = "black", size=12),
    legend.position=c(1,1),
    legend.justification=c(1,1)) +
  labs(x="Number of Dimensions", 
       y = "Recall@1", 
       title= "Linear Regression (95% CI) of Recall@1 vs Dimensions, by Category")

ggsave("recall_dim.jpeg", p3, width = 3.5, height = 3.5)