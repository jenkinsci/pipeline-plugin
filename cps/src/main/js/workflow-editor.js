var $ = require('jquery-detached').getJQuery();
var jenkinsJSModules = require('jenkins-js-modules');
var wrapper = $('#workflow-editor-wrapper');
var textarea = $('textarea', wrapper);

$('.textarea-handle', wrapper).remove();

jenkinsJSModules.import('ace-editor:ace-editor-122')
    .onFulfilled(function (acePack) {
        acePack.edit('workflow-editor', function() {
            var ace = acePack.ace;
            var editor = this.editor;

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

                var theScript = textarea.val();

                editor.setValue(theScript, 1);
                editor.getSession().on('change', function() {
                    textarea.val(editor.getValue());
                });

                if (theScript === '') {
                    var $aceEditor = $('#workflow-editor', wrapper);
                    var samples = $('<div><select>' +
                        '<option >try sample workflow...</option>' +
                        '<option value="hello">Hello World</option>' +
                        '<option value="maven">GitHub + Maven</option>' +
                        '</select></div>');

                    samples.insertBefore($aceEditor);
                    samples.css({
                        'position': 'absolute',
                        'right': '1px',
                        'z-index': 100,
                        'top': '1px'
                    });

                    var sampleSelect = $('select', samples);
                    sampleSelect.change(function() {
                        var theSample = require('./samples').getSample(sampleSelect.val());
                        editor.setValue(theSample, 1);
                    });
                }

            });
        });
    });

wrapper.show();
textarea.hide();
