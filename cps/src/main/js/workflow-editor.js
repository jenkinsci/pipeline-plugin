var $ = require('jqueryui-detached').getJQueryUI();
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
            var $wfEditor = $('#workflow-editor');
            $wfEditor.get(0).aceEditor = editor;

            acePack.addPackOverride('snippets/groovy.js', '../workflow-cps/snippets/workflow.js');

            acePack.addScript('ext-language_tools.js', function() {
                ace.require("ace/ext/language_tools");
                
                editor.$blockScrolling = Infinity;
                editor.session.setMode("ace/mode/groovy");
                editor.setTheme("ace/theme/tomorrow");
                editor.setAutoScrollEditorIntoView(true);
                editor.setOption("minLines", 20);
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

                // force underlying textarea onchange which turns in Stapler validation check
                editor.on('blur', function() {
                    //textarea.trigger('change');
                    editor.session.clearAnnotations();
                    var url = textarea.attr("checkUrl");
                    /*
                    var q = qs(this).addThis();
                    if (depends.length>0) {
                        depends.split(" ").each(function (n) {
                            q.nearBy(n);
                        });
                    }
                    url = url + q.toString();
                    */

                    $.ajax({
                        url: url,
                        data: {
                            value: editor.getValue(),
                            sandbox: $('_.sandbox').val()
                        },
                        method: textarea.attr("checkMethod") || "get",
                        success: function(data) {
                            if (data !== '<div/>') {
                                editor.getSession().setAnnotations([{
                                    row: 1,
                                    column: 1,
                                    text: data,
                                    type: "error" // also warning and information
                                }]);
                            }
                        }
                    });
                    /*
                    editor.getSession().setAnnotations([{
                        row: 1,
                        column: 1,
                        text: "Strange error",
                        type: "error" // also warning and information
                    }]);
                    */
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

            // Make the editor resizable using jQuery UI resizable (http://api.jqueryui.com/resizable).
            // ACE Editor doesn't have this as a config option.
            $wfEditor.wrap('<div class="jquery-ui-1"></div>');
            $wfEditor.resizable({
                handles: "s", // Only allow vertical resize off the bottom/south border
                resize: function() {
                    editor.resize();
                }
            });
            // Make the bottom border a bit thicker as a visual cue.
            // May not be enough for some people's taste.
            $wfEditor.css('border-bottom-width', '0.4em');
        });
    });

wrapper.show();
textarea.hide();
