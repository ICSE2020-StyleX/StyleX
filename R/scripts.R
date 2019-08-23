library(readr)
library(C50)
library(dplyr)
library(cluster)
library(apcluster)

init <- function() {
  path <- "data.csv";
  elements.table <<- read_delim(path, escape_backslash = F,
                                delim="|", escape_double = FALSE, comment = "*>", trim_ws = TRUE,
                                na = "NOT-GIVEN",
                                col_types = cols(
                                  .default = col_character(),
                                  align_content = col_factor(NULL),
                                  align_items = col_factor(NULL),
                                  align_self = col_factor(NULL),
                                  all = col_factor(NULL),
                                  animation_direction = col_factor(NULL),
                                  animation_fill_mode = col_factor(NULL),
                                  animation_play_state = col_factor(NULL),
                                  animation_timing_function = col_factor(NULL),
                                  backface_visibility = col_factor(NULL),
                                  background_attachment = col_factor(NULL),
                                  background_blend_mode = col_factor(NULL),
                                  background_clip = col_factor(NULL),
                                  background_origin = col_factor(NULL),
                                  background_repeat_x = col_factor(NULL),
                                  background_repeat_y = col_factor(NULL),
                                  border_block_end_style = col_factor(NULL),
                                  border_block_start_style = col_factor(NULL),
                                  border_bottom_style = col_factor(NULL),
                                  border_collapse = col_factor(NULL),
                                  border_inline_end_style = col_factor(NULL),
                                  border_inline_start_style = col_factor(NULL),
                                  border_left_style = col_factor(NULL),
                                  border_right_style = col_factor(NULL),
                                  border_top_style = col_factor(NULL),
                                  box_sizing = col_factor(NULL),
                                  clear = col_factor(NULL),
                                  cursor = col_factor(NULL),
                                  display = col_factor(NULL),
                                  flex_direction = col_factor(NULL),
                                  flex_grow = col_number(),
                                  flex_wrap = col_factor(NULL),
                                  float = col_factor(NULL),
                                  font_style = col_factor(NULL),
                                  font_weight = col_factor(NULL),
                                  hyphens = col_factor(NULL),
                                  justify_content = col_factor(NULL),
                                  list_style_position = col_factor(NULL),
                                  list_style_type = col_factor(NULL),
                                  mix_blend_mode = col_factor(NULL),
                                  object_fit = col_factor(NULL),
                                  opacity = col_double(),
                                  outline_style = col_factor(NULL),
                                  overflow_wrap = col_factor(NULL),
                                  overflow_x = col_factor(NULL),
                                  overflow_y = col_factor(NULL),
                                  pointer_events = col_factor(NULL),
                                  position = col_factor(NULL),
                                  resize = col_factor(NULL),
                                  table_layout = col_factor(NULL),
                                  text_align = col_factor(NULL),
                                  text_decoration_line = col_factor(NULL),
                                  text_decoration_style = col_factor(NULL),
                                  text_overflow = col_factor(NULL),
                                  text_rendering = col_factor(NULL),
                                  text_size_adjust = col_factor(NULL),
                                  text_transform = col_factor(NULL),
                                  touch_action = col_factor(NULL),
                                  transform_style = col_factor(NULL),
                                  transition_property = col_factor(NULL),
                                  unicode_bidi = col_factor(NULL),
                                  user_select = col_factor(NULL),
                                  visibility = col_factor(NULL),
                                  white_space = col_factor(NULL),
                                  widows = col_factor(NULL),
                                  will_change = col_factor(NULL),
                                  word_break = col_factor(NULL),

                                  color = col_factor(NULL),
                                  background_color = col_factor(NULL),
                                  
                                  boundingBox.x = col_double(),
                                  boundingBox.y = col_double(),
                                  boundingBox.width = col_double(),
                                  boundingBox.height = col_double(),
                                  depth = col_integer(),
                                  numberOfDescendants = col_integer(),
                                  subtreeHeight = col_integer(),
                                  candidateElementID = col_integer(),
                                  candidateElementTagName=col_factor(NULL),
                                  listener=col_factor(NULL),
                                  listener.mode=col_factor(NULL)
                                ) 
                              );
  
  #elements.table$background_color <<- normalize_color(elements.table$background_color)
  
  # Add some boolean values to the dataset 
  elements.table$has.animation <<- 
    elements.table$animation_name != "none" |
    (elements.table$transition_property != "none" &
       elements.table$transition_property != "all")
  elements.table$has.bg <<- 
    elements.table$background_image != "none" |
    elements.table$background_color != "rgba(0, 0, 0, 0)"
  elements.table$has.border <<- 
    elements.table$border_top_style != "none" |
    elements.table$border_bottom_style != "none" |
    elements.table$border_left_style != "none" |
    elements.table$border_right_style != "none"
  elements.table$has.box.shadow <<- elements.table$box_shadow != "none"
  elements.table$has.outline <<- elements.table$outline_style != "none"
  elements.table$has.text.decoration <<- elements.table$text_decoration_line != "none"
  elements.table$has.touch.action <<- 
    elements.table$touch_action != "auto"
  elements.table$has.cursor <<-
    elements.table$cursor != "default" &
    elements.table$cursor != "auto"
  elements.table$has.will.change <<- elements.table$will_change != "auto"
  elements.table$has.transform <<- elements.table$transform != "none" 
  elements.table$has.z.index <<- 
    elements.table$z_index != "0" &
    elements.table$z_index != "auto"
  
  cursor.factors <- c("alias",
                      "all-scroll",
                      "auto",
                      "cell",
                      "context-menu",
                      "col-resize",
                      "copy",
                      "crosshair",
                      "default",
                      "e-resize",
                      "ew-resize",
                      "grab",
                      "grabbing",
                      "help",
                      "move",
                      "n-resize",
                      "ne-resize",
                      "nesw-resize",
                      "ns-resize",
                      "nw-resize",
                      "nwse-resize",
                      "no-drop",
                      "none",
                      "not-allowed",
                      "pointer",
                      "progress",
                      "row-resize",
                      "s-resize",
                      "se-resize",
                      "sw-resize",
                      "text",
                      "w-resize",
                      "wait",
                      "zoom-in",
                      "zoom-out",
                      "custom")
  
  elements.table$cursor <<- normalize.cursor.values(elements.table$cursor, cursor.factors)
  elements.table <<- elements.table[!is.na(elements.table$cursor),]
  
  
  model.formula <<- has.listener ~
    align_content +
    align_items +
    align_self +
    backface_visibility +
    border_block_end_style +
    border_block_start_style +
    border_bottom_style +
    border_collapse +
    border_inline_end_style +
    border_inline_start_style +
    border_left_style +
    border_right_style +
    border_top_style +
    box_sizing +
    clear +
    cursor +
    display +
    flex_direction +
    flex_grow +
    flex_wrap +
    float +
    font_style +
    font_weight +
    hyphens +
    justify_content +
    list_style_position +
    list_style_type +
    mix_blend_mode +
    object_fit +
    opacity +
    outline_style +
    overflow_wrap +
    overflow_x +
    overflow_y +
    pointer_events +
    position +
    resize +
    table_layout +
    text_align +
    text_decoration_line +
    text_decoration_style +
    text_overflow +
    text_rendering +
    text_size_adjust +
    text_transform +
    transform_style +
    unicode_bidi +
    user_select +
    visibility +
    white_space +
    word_break +
    
    boundingBox.x +
    boundingBox.y +
    boundingBox.width +
    boundingBox.height +
    depth +
    numberOfDescendants +
    subtreeHeight +
    
    has.animation +
    has.bg +
    has.border +
    has.box.shadow +
    has.outline +
    has.text.decoration +
    has.touch.action + 
    has.transform +
    has.will.change +
    has.z.index
}

normalize.cursor.values <- function(cursor.value, cursor.factors) {
  cursor.df <- data.frame(cursor = as.character(cursor.value), stringsAsFactors = F)
  cursor.df$cursor[!(cursor.df$cursor %in% cursor.factors)] <- "custom"
  return(factor(cursor.df$cursor, levels = cursor.factors))
}

init.data <- function(listener="click") {
  elements <- elements.table
  elements$uniqueCandidateElementID <- paste(elements$website, elements$candidateElementID, sep = "-")
  elements$listener <- factor(elements$listener, 
                              levels = c(levels(elements$listener), NA), 
                              labels = c(levels(elements$listener), ""), 
                              exclude = NULL)
  
  allTrue <- elements[elements$listener == listener, ]
  allTrue <- allTrue[,!(names(allTrue) %in% c("listener", "listener.mode"))]
  allTrue$has.listener <- TRUE
  
  allFalse <- elements[!(elements$uniqueCandidateElementID %in% allTrue$uniqueCandidateElementID), ]
  allFalse <- allFalse[,!(names(allFalse) %in% c("listener", "listener.mode"))]
  allFalse <- distinct(allFalse, uniqueCandidateElementID, .keep_all = T)
  allFalse$has.listener <- FALSE

  cat(sprintf("For event handler %s, true instances: %s, false instances: %s\n",
              listener,
              nrow(allTrue),
              nrow(allFalse)))
  
  #set.seed(101)
  allWebsites <- data.frame(website = unique(elements$website))
  #trainingWebsitesIndices <- sample(nrow(allWebsites), nrow(allWebsites) * 0.8)
  trainingWebsitesIndices <- 1:799
  trainingWebsites <- allWebsites$website[trainingWebsitesIndices]
  testingWebsites <- allWebsites$website[-trainingWebsitesIndices]
  
  #allTrueSampleRows <- sample(nrow(allTrue), nrow(allTrue) * 0.8)
  trainingTrue <- allTrue[allTrue$website %in% trainingWebsites, ]
  testingTrue <- allTrue[allTrue$website %in% testingWebsites, ]
  
  trainingFalse <- allFalse[allFalse$website %in% trainingWebsites, ]
  underSampledRows <- sample(nrow(trainingFalse), nrow(trainingTrue))
  trainingFalse <- trainingFalse[underSampledRows, ]
  
  testingFalse <- allFalse[allFalse$website %in% testingWebsites, ]
  
  training <<- rbind(trainingTrue, trainingFalse)
  testing <<- rbind(testingTrue, testingFalse)
  
}

build.models <- function() {
  init()
  event.listeners.df <- as.data.frame(table(elements.table$listener))
  event.listeners <- as.vector(head(event.listeners.df[order(-event.listeners.df$Freq), c("Var1")], 10))
  
  for (event.listener in event.listeners) {
    print(Sys.time())
    init.data(event.listener)
    c50.model(event.listener)
  }
  rm(elements.table, testing, training, c50.model, init, init.data, build.models, model.formula, normalize.cursor.values,
     envir = .GlobalEnv)
}

c50.model <- function(fit.model.name = "fit.c50", training.table = training, testing.table = testing, training.formula = model.formula) {
  
  training.table$has.cursor <- ifelse(training.table$has.cursor, 1, 0)
  training.table$has.animation <- ifelse(training.table$has.animation, 1, 0)
  training.table$has.bg <- ifelse(training.table$has.bg, 1, 0)
  training.table$has.box.shadow <- ifelse(training.table$has.box.shadow, 1, 0)
  training.table$has.outline <- ifelse(training.table$has.outline, 1, 0)
  training.table$has.border <- ifelse(training.table$has.border, 1, 0)
  training.table$has.text.decoration <- ifelse(training.table$has.text.decoration, 1, 0)
  training.table$has.touch.action <- ifelse(training.table$has.touch.action, 1, 0)
  training.table$has.transform <- ifelse(training.table$has.transform, 1, 0)
  training.table$has.will.change <- ifelse(training.table$has.will.change, 1, 0)
  training.table$has.z.index <- ifelse(training.table$has.z.index, 1, 0)
  training.table$has.listener <- as.factor(training.table$has.listener)
  
  testing.table$has.cursor <- ifelse(testing.table$has.cursor, 1, 0)
  testing.table$has.animation <- ifelse(testing.table$has.animation, 1, 0)
  testing.table$has.bg <- ifelse(testing.table$has.bg, 1, 0)
  testing.table$has.box.shadow <- ifelse(testing.table$has.box.shadow, 1, 0)
  testing.table$has.outline <- ifelse(testing.table$has.outline, 1, 0)
  testing.table$has.border <- ifelse(testing.table$has.border, 1, 0)
  testing.table$has.text.decoration <- ifelse(testing.table$has.text.decoration, 1, 0)
  testing.table$has.touch.action <- ifelse(testing.table$has.touch.action, 1, 0)
  testing.table$has.transform <- ifelse(testing.table$has.transform, 1, 0)
  testing.table$has.will.change <- ifelse(testing.table$has.will.change, 1, 0)
  testing.table$has.z.index <- ifelse(testing.table$has.z.index, 1, 0)
  testing.table$has.listener <- as.factor(testing.table$has.listener)
  
  
  fit <- C5.0(formula = training.formula, 
              data = training.table, 
              trials=10,
              rules=F)
  print(fit)
  
  assign(fit.model.name, fit, envir = .GlobalEnv)
  
  pred <- predict(fit, testing.table, type = "class")
  
  confustion.matrix <- as.data.frame(table(pred, testing.table$has.listener))
  colnames(confustion.matrix) <- c("predicted", "actual", "count")
  print(confustion.matrix)
  
  cat(sprintf("Precision of the TRUE class for %s: %s\n",
              fit.model.name,
              confustion.matrix$count[confustion.matrix$predicted==T & confustion.matrix$actual==T] /
                sum(confustion.matrix$count[confustion.matrix$predicted==T])))
  
  cat(sprintf("Recall of the TRUE class for %s: %s\n",
              fit.model.name,
              confustion.matrix$count[confustion.matrix$predicted==T & confustion.matrix$actual==T] /
                sum(confustion.matrix$count[confustion.matrix$actual==T])))
  
  cat(sprintf("Precision of the FALSE class for %s: %s\n",
              fit.model.name,
              confustion.matrix$count[confustion.matrix$predicted==F & confustion.matrix$actual==F] /
                sum(confustion.matrix$count[confustion.matrix$predicted==F])))
  
  cat(sprintf("Recall of the FALSE class for %s: %s\n",
              fit.model.name,
              confustion.matrix$count[confustion.matrix$predicted==F & confustion.matrix$actual==F] /
                sum(confustion.matrix$count[confustion.matrix$actual==F])))

  cat(sprintf("Total number of TRUE instances in testing data for %s: %s\n", 
              fit.model.name,
              nrow(testing.table[testing.table$has.listener=="TRUE",])))
  
  cat(sprintf("Accuracy on testing data for %s: %s\n", 
              fit.model.name,
              mean(pred == testing.table$has.listener)))
  
  print(C5imp(fit))
  
}

predict.handlers <- function(values) {
  all.fits <- ls(envir = .GlobalEnv)
  lst <- list()
  for (fit in all.fits[all.fits != match.call()[[1]] & all.fits != "cluster.elements"]) {
    model <- get(fit, envir = .GlobalEnv)
    #print(fit)
    lst[[fit]] <- predict(model, values, type = "class")
  }
  return(lst)
}

cluster.elements <- function(values) {
  distance.object <- daisy(values, metric = "gower", stand = F)
  cluster.object <- apcluster(as.matrix(1 - distance.object), details = F)
  return(as.vector(attr(cluster.object, "exemplars")))
}
