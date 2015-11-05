var samples = [];

exports.addSamplesWidget = function(editor) {
    var $ = require('jquery-detached').getJQuery();

    if ($('#workflow-editor-wrapper .samples').length) {
        // Already there.
        return;
    }

    var $aceEditor = $('#workflow-editor');
    var sampleSelect = $('<select></select>');
    
    sampleSelect.append('<option >try sample workflow...</option>');
    for (var i = 0; i < samples.length; i++) {
        sampleSelect.append('<option value="' + samples[i].name + '">' + samples[i].title + '</option>');
    }
    
    var samplesDiv = $('<div class="samples"></div>');
    samplesDiv.append(sampleSelect);

    samplesDiv.insertBefore($aceEditor);
    samplesDiv.css({
        'position': 'absolute',
        'right': '1px',
        'z-index': 100,
        'top': '1px'
    });

    sampleSelect.change(function() {
        var theSample = getSample(sampleSelect.val());
        editor.setValue(theSample, 1);
    });    
};

function getSample(sampleName) {
    for (var i = 0; i < samples.length; i++) {
        if (samples[i].name === sampleName) {
            return samples[i].script;
        }
    }
    return '';
}

samples.push({
    name: 'hello',
    title: 'Hello World',
    script: "node {\n" +
        "   stage 'Stage 1'\n" +
        "   echo 'Hello World 1'\n" +
        "   stage 'Stage 2'\n" +
        "   echo 'Hello World 2'\n" +
        "}"
}); 

samples.push({
    name: 'github-maven',
    title: 'GitHub + Maven',
    script: "node {\n" +
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
        "}"
});