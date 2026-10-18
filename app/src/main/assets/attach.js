(function(doc){var r={attached:0,inputs:[],blockedFrames:0};
function toFile(){var bin=atob(doc.b64),u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);return new File([u],doc.name,{type:doc.mime,lastModified:Date.now()});}
function run(d){d.querySelectorAll('input[type=file]').forEach(function(e){
 var info={field:e.name||e.id||'file',accept:e.getAttribute('accept')||'any'};
 if(e.disabled){info.result='disabled';r.inputs.push(info);return;}
 try{var dt=new DataTransfer();dt.items.add(toFile());e.files=dt.files;
  ['input','change'].forEach(function(t){e.dispatchEvent(new Event(t,{bubbles:true}));});
  info.result=(e.files.length===1&&e.files[0].name===doc.name)?'attached':'not accepted';
  if(info.result==='attached'){r.attached++;var p=e.offsetParent?e:e.parentElement;if(p)p.style.outline='3px solid #2563eb';}
 }catch(x){info.result='error: '+x.message;}
 r.inputs.push(info);});
 d.querySelectorAll('iframe').forEach(function(f){try{run(f.contentDocument);}catch(x){r.blockedFrames++;}});}
run(document);return JSON.stringify(r);})
