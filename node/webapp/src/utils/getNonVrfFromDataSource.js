import {extractTaskHttpGet} from "./extractTaskHttpGet";

export function getNonVrfFromDataSource(data, node) {
  let dataSource = [];
  data.forEach((item, index) => {
    if(item.initiators[0].type !== "randomnesslog") {
      dataSource.push({
        key: index,
        ID: item.id,
        Initiator: item.initiators[0].type,
        Created: item.createdAt,
        Node: node.text,
        DataSource: extractTaskHttpGet(item) ,
      });
    }
  });
  return dataSource;
}