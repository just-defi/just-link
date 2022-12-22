export function extractTaskHttpGet(data, dataSourceArr) {
  let task = JSON.parse(data.params).tasks[0];
  if(task.type === "httpget") {
    let httpGetUrl = task.params.get
    return dataSourceArr.find(obj => httpGetUrl.includes(obj.value)).value.toUpperCase();
  } else if(task.type === "justswap") {
    return  task.type.toUpperCase();
  }
}