const dateSort = (a, b, sortOrder) => {
  return sortOrder === "descend" ? a - b : ((b - a) * -1);
}


const normalSort = (a, b, sortOrder) => {
  if (sortOrder === "descend") {
    if (a < b) return 1;
    if (a > b) return -1;
    return 0;
  } else {
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
  }

}

const stringSort = (a, b, sortOrder) => {
  return sortOrder === "descend" ? (b.localeCompare(a)* -1) : a.localeCompare(b);
}

export const Sorter = {
  DEFAULT: normalSort,
  DATE: dateSort,
  STRING: stringSort,
};