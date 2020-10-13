export function isJSON(str) {
  if (typeof str == 'string') {
    try {
      var obj=JSON.parse(str);
      if(typeof obj == 'object' && obj ){
        return true;
      }else{
        return false;
      }

    } catch(e) {
      return false;
    }
  }
  console.log('It is not a string!')
}
