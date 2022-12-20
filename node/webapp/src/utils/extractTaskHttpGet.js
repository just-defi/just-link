export function extractTaskHttpGet(data) {
  let task = JSON.parse(data.params).tasks[0];
  if(task.type === "httpget") {
    return task.params.get;
  } else if(task.type === "justswap") {
    return  task.params.pool;
  }
}