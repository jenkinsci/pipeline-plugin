//
// See https://github.com/tfennelly/jenkins-js-builder
//
var builder = require('jenkins-js-builder');

//
// Bundle the modules.
// See https://github.com/tfennelly/jenkins-js-builder#bundling
//
builder.bundle('src/main/js/workflow-editor.js')
    .withExternalModuleMapping('jquery-detached', 'jquery-detached:jquery2')
    .inDir('target/generated-resources/adjuncts/org/jenkinsci/plugins/workflow/cps');
