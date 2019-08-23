# StyleX

## Source code
StyleX is a plugin on top of a modified version of [Crawljax](https://github.com/crawljax/crawljax). The plugin's source code can be found under the `StyleX/crawljax/stylex` folder.

### Running StyleX
1. Install R.
2. Install the following packages in R: `rJava`, `apcluster`, `cluster`, and `C50`.
3. Have `R_HOME=/Library/Frameworks/R.framework/Resources` in environemnt variables (the folder should point to the place where R has been installed. This is required by `rJava`).
4. Run `com.stylex.visualcrawler.App.StyleXMain`. The required CLI parameters will be shown up.

Alternatively, one can run `mvn package -DskipTests` in the `StyleX/crawljax` folder. A fat jar will be generated under `StyleX/crawljax/stylex/target` (`jar-with-dependencies`) which can be run from the commandline.

## R scripts
The scripts used for training machine learning models can be found under the `R` folder.

## Data
The `Data` folder conatains the data used for training machine learning models (`data.csv.zip`). The data collected during the crawls can be found [here](https://drive.google.com/uc?export=download&id=1lQJfczdJTm0_73vcKHjp2fzWa6BBxYTL).


