var $ = require('jquery-detached').getJQuery();
var jenkinsJSModules = require('jenkins-js-modules');
var wrapper = $('#workflow-editor-wrapper');
var textarea = $('textarea', wrapper);

$('.textarea-handle', wrapper).remove();

// The Jenkins 'ace-editor:ace-editor-122' plugin doesn't support a synchronous 
// require option. This is because of how the ACE editor is written. So, we need
// to use lower level jenkins-js-modules async 'import' to get a handle on a 
// specific version of ACE, from which we create an editor instance for workflow. 
jenkinsJSModules.import('ace-editor:ace-editor-122')
    .onFulfilled(function (acePack) {
        
        // The 'ace-editor:ace-editor-122' plugin supplies an "ACEPack" object.
        // ACEPack understands the hardwired async nature of the ACE impl and so
        // provides some async ACE script loading functions.
        
        acePack.edit('workflow-editor', function() {
            var ace = acePack.ace;
            var editor = this.editor;
            
            // Attach the ACE editor instance to the element. Useful for testing.
            $('#workflow-editor').get(0).aceEditor = editor;

            acePack.addPackOverride('snippets/groovy.js', '../workflow-cps/snippets/workflow.js');

            acePack.addScript('ext-language_tools.js', function() {
                ace.require("ace/ext/language_tools");
                
                editor.$blockScrolling = Infinity;
                editor.session.setMode("ace/mode/groovy");
                editor.setTheme("ace/theme/tomorrow");
                editor.setAutoScrollEditorIntoView(true);
                editor.setOption("minLines", 20);
                editor.setOption("maxLines", 20);
                // enable autocompletion and snippets
                editor.setOptions({
                    enableBasicAutocompletion: true,
                    enableSnippets: true,
                    enableLiveAutocompletion: false
                });

                editor.setValue(textarea.val(), 1);
                editor.getSession().on('change', function() {
                    textarea.val(editor.getValue());
                    showSamplesWidget();
                });
                
                function showSamplesWidget() {
                    // If there's no workflow defined (e.g. on a new workflow), then
                    // we add a samples widget to let the user select some samples that
                    // can be used to get them going.
                    if (editor.getValue() === '') {
                        require('./samples').addSamplesWidget(editor);
                    }
                }
                showSamplesWidget();
            });
        });
    });

wrapper.show();
textarea.hide();
