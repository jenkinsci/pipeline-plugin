var samples = {};

exports.getSample = function(sampleName) {
    var sample = samples[sampleName];
    if (sample) {
        return sample;
    } else {
        return '';
    }
};

samples.hello = "node {\n" +
    "   stage 'Stage 1'\n" +
    "   echo 'Hello World 1'\n" +
    "   stage 'Stage 2'\n" +
    "   echo 'Hello World 2'\n" +
    "}";

samples.maven = "node {\n" +
    "   // Mark the code checkout 'stage'....\n" +
    "   stage 'Checkout'\n" +
    "\n" +
    "   // Get some code from a GitHub repository\n" +
    "   git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'\n" +
    "\n" +
    "   // Get the maven tool.\n" +
    "   // ** NOTE: This 'M3' maven tool must be configured\n" +
    "   // **       in the global configuration.           \n" +
    "   def mvnHome = tool 'M3'\n" +
    "\n" +
    "   // Mark the code build 'stage'....\n" +
    "   stage 'Build'\n" +
    "   // Run the maven build\n" +
    "   sh \"${mvnHome}/bin/mvn clean install\"\n" +
    "}";