export function getVrfFromDataSource(data, node) {
  let dataSource = [];

  data.forEach((item, index) => {
    if(item.initiators[0].type === "randomnesslog") {
      dataSource.push({
        key: index,
        ID: item.id,
        Initiator: item.initiators[0].type,
        Created: item.createdAt,
        Node: node.text,
        DataSource: item,
      });
    }
  });
  return dataSource;
}