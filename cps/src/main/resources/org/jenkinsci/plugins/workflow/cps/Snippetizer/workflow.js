var refDslHlp = {	
  parseDom:function(){
    $$('dl.root > dt').each(function(elem){
      elem.observe('click',refDslHlp.minimizeClick); 
    });
  },
  
  minimizeClick:function(){
    var elem = this;
    var nextElem = elem.next();
    var height = nextElem.getHeight();
    if(elem.hasClassName('show-minimize')){
      elem.removeClassName('show-minimize');
      nextElem.removeClassName('minimize');
    }
    else{
      nextElem.setStyle({height:height+'px'});
      setTimeout(function(){
        elem.addClassName('show-minimize');
        nextElem.addClassName('minimize');
      },10);
    }    
  }
}

refDslHlp.parseDom();
