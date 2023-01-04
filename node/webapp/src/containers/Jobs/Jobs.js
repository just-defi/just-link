import React, {Component, Fragment} from 'react';
import {
  Button,
  Input,
  Modal,
  PageHeader,
  Row,
  Select,
  Table,
  Tag,
  Icon,
  Tabs,
  Tooltip
} from 'antd';
import xhr from "axios/index";
import $ from 'jquery';
import {isJSON} from "../../utils/isJson";

const {TextArea} = Input;
const {Option} = Select;
const {TabPane} = Tabs;

const API_URL = process.env.API_URL;
const API_URLS = process.env.API_URLS;
const DS_SIZE = process.env.DATASOURCE_SIZE_PER_RETRIEVAL;
const RANDOMNESS_LOG = 'randomnesslog';
class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: false,
      warning: false,
      visible: false,
      vrfDataSource: [],
      dataSource: [],
      nestedDataSource: [],
      searchText: '',
      searchedColumn:'',
      size: DS_SIZE,
      jobUrl: API_URL,
      providerList: [],
      filteredInfo: null,
      sortedInfo: null,
    };
  };

  componentDidMount() {
    this.getJobs(this.state.size);
    if (this.props.location.state && this.props.location.state.create) {
      this.setState({jobUrl: this.props.location.state.jobUrl});
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue: JSON.stringify(input, null, 4)});
    }
    window.sessionStorage.removeItem('jobUrl');
    window.sessionStorage.removeItem('nodeName:q');
  };

  componentWillReceiveProps() {
  };

  getColumnSearchProps = dataIndex => ({
    filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
        <div style={{ padding: 8 }}>
          <Input
              ref={ node => {
                this.searchInput = node;
              }}
              placeholder={`Search ${dataIndex}`}
              value={selectedKeys[0]}
              onChange={e => setSelectedKeys(e.target.value ? [e.target.value]: [])}
              onPressEnter={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              style={{ width: 188, marginBottom: 8, display: 'block' }}
          />
          <Button
              type = 'primary'
              onClick={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              icon="search"
              size="small"
              style={{ width: 90, marginRight: 8 }}
          >
            Search
          </Button>
          <Button
              onClick={() => this.handleReset(clearFilters)}
              size="small"
              style={{ width: 90 }}
          >
            Reset
          </Button>
        </div>
    ),
    filterIcon: filtered => (
        <Icon type="search" style={{ color: filtered ? '#1890ff' : undefined }}/>
    ),
    onFilter: (value, record) => {
      if (dataIndex === "Contract") {
      return  record[dataIndex].toString().includes(value)
      } else {
        const arr = [];
        console.log(record);
        console.log(value);
        record.children.forEach((item)=>{
          return item[dataIndex].toString().includes(value);

        });
      }

    },
    onFilterDropdownVisibleChange: visible => {
      if(visible) {
        setTimeout(() => this.searchInput.select());
      }
    },
    render(text) {
      return text;
    },
  });

  getColumnSearchPropsChildren = dataIndex => ({
    filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
        <div style={{ padding: 8 }}>
          <Input
              ref={ node => {
                this.searchInput = node;
              }}
              placeholder={`Search ${dataIndex}`}
              value={selectedKeys[0]}
              onChange={e => setSelectedKeys(e.target.value ? [e.target.value]: [])}
              onPressEnter={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              style={{ width: 188, marginBottom: 8, display: 'block' }}
          />
          <Button
              type = 'primary'
              onClick={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              icon="search"
              size="small"
              style={{ width: 90, marginRight: 8 }}
          >
            Search
          </Button>
          <Button
              onClick={() => this.handleReset(clearFilters)}
              size="small"
              style={{ width: 90 }}
          >
            Reset
          </Button>
        </div>
    ),
    filterIcon: filtered => (
        <Icon type="search" style={{ color: filtered ? '#1890ff' : undefined }}/>
    ),
    onFilter: (value, record) => {

    },
    onFilterDropdownVisibleChange: visible => {
      if(visible) {
        setTimeout(() => this.searchInput.select());
      }
    },
    render(text) {
      return text;
    },
  });

  handleSearch = (selectedKeys, confirm, dataIndex) => {
    confirm();
    this.setState({
      searchText: selectedKeys[0],
      searchedColumn: dataIndex,
    });
  }

  handleReset = clearFilters => {
    clearFilters();
    this.setState({ searchText: '' });
  };

  getJobs = (size) => {
    this.setState({loading: true});
    try {
      API_URLS.forEach((api) => {
        let url = api.value + "/job/specs?size=" + size;
        xhr.get(url).then(
            (response) => {
              console.log("GJ: ", response.data.data);
              let data = response.data.data;
              data.forEach((item) => {
                this.createJob(item, api);
              })
              this.setState({
                loading: false,
              });
            });
      });
    } catch (e) {

    }
  };

  getLatestResultAndSetToSourceArr(jobId, api, job) {
      let url = api.value + "/job/result/" + jobId;
      xhr.get(url).then((response) => {
        if (response.status === 200) {
          job.LastRunResult.value = response.data.data;
          job.LastRunResult.url = url;
          this.filterJobsAndSetToState(job);
        } else {
          job.LastRunResult.value = 0;
          job.LastRunResult.url = url;
          this.filterJobsAndSetToState(job);
        }
      });
  }

  createJob = (data, api) => {
    const type = data.initiators[0].type;
    const contractAddr = data.initiators[0].address;
    let tasks = JSON.parse(data.params).tasks.map(task => task.type).join(" / ");
    const dataSourceEndPoint = this.generateTagColor(JSON.parse(data.params).tasks[0]);
    const jobId = data.id;
    const createdDate = data.createdAt;
    const updatedDate = data.updatedAt;
    let job = {
      key: crypto.randomUUID(),
      Contract: contractAddr,
      ID: jobId,
      Initiator: type,
      Created: createdDate,
      Updated: updatedDate,
      Node: api.text,
      DataSource: {task: tasks, ...dataSourceEndPoint},
      LastRunResult: {value: "", url:""},
    };
    this.getLatestResultAndSetToSourceArr(data.id, api, job);

  }

  filterJobsAndSetToState = (job) => {
    if (job.Initiator === RANDOMNESS_LOG) {
      this.setState({
        vrfDataSource: [...this.state.vrfDataSource, job],
      });
    } else {
      // this.setState({
      //   dataSource: [...this.state.dataSource, job],
      // });
      const nestedData = {
        Contract: job.Contract,
        children: [{
          key: job.key,
          Contract: null,
          ID: job.ID,
          Initiator: job.Initiator,
          Created: job.Created,
          Updated: job.Updated,
          Node: job.Node,
          DataSource: job.DataSource,
          LastRunResult: job.LastRunResult,
        }],
      }

      if(this.state.nestedDataSource.length <= 0) {
        this.state.nestedDataSource.push(nestedData);
      } else {
        let result = this.state.nestedDataSource.findIndex(item => {
          return item.Contract === nestedData.Contract
        });
        if (result < 0) {
          this.state.nestedDataSource.push(nestedData);
        }else {
          this.state.nestedDataSource[result].children = [...this.state.nestedDataSource[result].children, ...nestedData.children];

          this.setState({dataSource: JSON.parse(JSON.stringify(this.state.nestedDataSource))});
        }
      }
    }
  }

  onSelectChange = (e) => {
    console.log(this.state);
    this.setState({jobUrl: e});
  };

  showModal = () => {
    this.setState({
      visible: true,
      textValue: ''
    });
  };

  handleOk = async (e) => {

    if (!isJSON(this.state.textValue)) {
      this.setState({warning: true});
      return;
    }
    this.setState({warning: false, visible: false});

    await xhr.post(this.state.jobUrl+"/job/specs", JSON.parse(this.state.textValue)).then((result) => {

     if (result.error) {
       this.error(result.error)
     }
     if (result.data.msg === 'success') {
       this.success()
     } else {
       this.error(result.data.msg)
     }
   }).catch((e) => {
     this.error(e.toString())
   });
  };

  handleCancel = e => {
    this.setState({
      visible: false,
    });
    delete this.props.location.state;
  };

  onTextChange = (e) => {
    this.setState({textValue: e.target.value})
  };

  success = (message) => {
    Modal.success({
      content: 'Successful!',
    });
  };

  error = (message) => {
    Modal.error({
      content: message,
    });
  };

  generateTagColor = (task) => {
    let url, tag, color = "";

    url = (task.type === "httpget" || task.type === "converttrx") ? task.params.get : task.type;

    try {
      const host = new URL(url).host;
      tag = host.split(".").at(-2).toUpperCase();
      color = "blue";
    } catch (e) {
      if (e instanceof TypeError) {
        tag = url.toUpperCase();
        switch (tag) {
          case "JUSTSWAP":
            color = "cyan";
            break;
          case "RANDOM":
            tag = "VRF";
            color = "green";
            break;
          default:
            tag = "UNKNOWN";
            color = "orange";
        }
      } else {
        tag = "UNKNOWN";
        color = 'red';
      }
    }

    let exists = this.state.providerList.findIndex(o => o.text === tag);

    if (exists === -1) {
      this.setState({
        providerList: [...this.state.providerList, {text: tag, value: tag}],
      });
    }

    return { tag: tag,color: color};
  }

  getCurrenRunResultUrl = (lastRunResult) => {
    if (lastRunResult) {
      return <a href={lastRunResult}>Current Run Result</a>;
    }
  }

  getDatasourceTag = (dataSource) => {

    if (dataSource) {
      return <span>
                <Tooltip title={dataSource.task} overlayStyle={{'white-space': 'nowrap', maxWidth: '500px'}} >
                  <Tag color={dataSource.color}>
                    {dataSource.tag}
                  </Tag>
                </Tooltip>
             </span>
    }
  }

  viewDetailOnClick = (record) => {
    console.log("VDOC: ",record);
    let selectedNode = API_URLS.find(url => url.text === record.Node).value;
    window.sessionStorage.setItem('jobUrl', selectedNode);
    window.sessionStorage.setItem('nodeName', record.Node);
    this.props.history.push({pathname: "/jobs/" +record.ID, jobUrl: selectedNode, nodeName: record.Node});
  }

  handleChange = (pagination, filters, sorter) => {
    this.setState({
      filteredInfo: filters,
      sortedInfo: sorter,
      dataSource: JSON.parse(JSON.stringify(this.state.dataSource))
    })
  }

  render() {
    let {
      visible,
      loading,
      textValue,
      warning,
      dataSource,
      nestedDataSource,
      size,
      vrfDataSource,
      providerList,
      filteredInfo,
      sortedInfo,
    } = this.state;

    sortedInfo = sortedInfo || {};
    filteredInfo = filteredInfo || {};

    //TODO change from using onRow to go to job Details and use ViewDetail button in the next commit
    const columns = [
      {
        title: 'Contract Address',
        dataIndex: 'Contract',
        key: 'Contract',
        ...this.getColumnSearchProps('Contract'),
        sorter: (a, b) => a.Contract > b.Contract ,
        render: (text, row, index) => {
          return {
            children: text,
            props: {
              colSpan: 1
            }
          };
        }
      },
      {
        title: 'Node',
        dataIndex: 'Node',
        key: 'Node',
        filters: API_URLS,
        filteredValue: filteredInfo.Node || null,
        onFilter: (value, record) => {
          this.state.nestedDataSource.forEach((item) => {
            //value = API_URLS.text
            //citem.Node = API_URLS.value
            item.children.forEach((citem, cindex, cobject) => {
              console.log("STATE :", this.state);
              let result = API_URLS.find(item => item.text == citem.Node);
              console.log("AFTER MAPPING: ", result.value + " filteredInfo ", filteredInfo.Node);

              if (filteredInfo &&
                  filteredInfo.Node
                  && filteredInfo.Node.indexOf(result.value) === -1) {
                console.log(filteredInfo.Node.indexOf(result.value) === -1);
                cobject.splice(cindex,1);
                console.log("After Slice: ", JSON.stringify(cobject) + " Length: ", cobject.length);
              }
            });

            //   if (
            //       filteredInfo &&
            //       filteredInfo.Node &&
            //       filteredInfo.Node.indexOf(citem.Node) === -1
            //   ) {
            //     cobject.forEach((item)=> console.log("COBJECT ITEMS: ", item))
            //     cobject.splice(cindex, 1);
            //     console.log("After: ",cobject);
            //   }
            });
          return true;
        },
        render: (text, row, index) => {
          return {
            children: text,
            props:{
              //colSpan: 1,
            }
          };
        }
        // onFilter: (record, {Node}) => {
        //   return record.toLowerCase().includes(API_URLS.find((url)=> url.text === Node).value);
        // },
      },
      {
        title: 'Job ID',
        dataIndex: 'ID',
        ...this.getColumnSearchProps('ID'),
        key: 'ID',
      },
      {
        title: 'Last Updated time',
        dataIndex: 'Updated',
        key: 'Updated',
        sorter: (a, b) => new Date(a.Created) - new Date(b.Created),
      },
      {
        title: 'Last Run Time',
        dataIndex: 'Created',
        key: 'Created',
        sorter: (a, b) => new Date(a.Created) - new Date(b.Created),
      },
      {
        title: 'Last Run Result',
        dataIndex: 'LastRunResult.value',
        key: 'LastRunResult',
      },
      {
        title: 'Current Result',
        dataIndex: 'LastRunResult.url',
        key: 'LastRunResult',
        render: this.getCurrenRunResultUrl
      },
      {
        title: 'Data Source',
        dataIndex: 'DataSource',
        key: 'DataSource',
        filters: providerList,
        onFilter: (value, record) => value.toUpperCase().includes(record.DataSource.tag.toUpperCase()),
        render: this.getDatasourceTag
      },
      {
        title: 'View & Edit',
        render: () => {<a onClick={this.showModal}>View and Edit</a>}
      },
      {
        title: 'View Detail',
        // render: (record) => {
        //   {
        //     return <a onClick={this.viewDetailOnClick(record)}>View Detail</a>;
        //   }
        // }

      }
    ];
    const pageSizeOption = ['10','20','30','40',size.toString()];

    return <Fragment>
      <PageHeader title="Jobs">

        <Button size='large' onClick={this.showModal} style={{float: 'right'}}>
          Create Job
        </Button>

      </PageHeader>

      <Tabs defaultActiveKey="1" >
      <TabPane tab="Price Feed Jobs" key="1">
      <Row gutter={16}>

        <Table
            dataSource={nestedDataSource}
            columns={columns}
            loading={loading}
            onChange={this.handleChange}
            defaultExpandAllRows={true}
            pagination={{
              pageSizeOptions: pageSizeOption,
              showSizeChanger: true,
              showQuickJumper: true,
              hideOnSinglePage: false,
            }}
            scroll={{ y: "calc(100vh - 380px)" }}
            onRow={record => {
              return {
                onClick: event => {
                  console.log(record);
                  let selectedNode = API_URLS.find(url => url.text === record.Node).value;
                  window.sessionStorage.setItem('jobUrl', selectedNode);
                  window.sessionStorage.setItem('nodeName', record.Node);
                  this.props.history.push({pathname: "/jobs/" +record.ID, jobUrl: selectedNode, nodeName: record.Node});
                },
                onMouseEnter: (event) => {
                  $(event.target).css('cursor', 'pointer');
                },
              };
            }}
        />


      </Row>
      </TabPane>
      <TabPane tab="VRF" key="2">

        <Table
            dataSource={vrfDataSource}
            columns={columns}
            loading={loading}

            pagination={{
              pageSizeOptions: pageSizeOption,
              showSizeChanger: true,
              showQuickJumper: true,
              hideOnSinglePage: false,
            }}
            onRow={record => {
              return {
                onClick: event => {
                  let selectedNode = API_URLS.find(url => url.text === record.Node).value;
                  window.sessionStorage.setItem('jobUrl', selectedNode);
                  window.sessionStorage.setItem('nodeName', record.Node);
                  this.props.history.push({pathname: "/jobs/" +record.ID, jobUrl: selectedNode, nodeName: record.Node});
                },
                onMouseEnter: (event) => {
                  $(event.target).css('cursor', 'pointer');
                },
              };
            }}
        />

      </TabPane>
      </Tabs>

      <Modal
          visible={visible}
          title="Create Job"
          onOk={this.handleOk}
          onCancel={this.handleCancel}
          footer={[
            <Button key="back" onClick={this.handleCancel}>
              Cancel
            </Button>,
            <Button key="submit" type="primary" onClick={this.handleOk}>
              Create
            </Button>
          ]}
      >
        <div>
          <span>Host Node</span>
          <Select
              defaultActiveFirstOption={true}
              defaultValue={this.state.jobUrl}
              autoFocus={true}
              onSelect={this.onSelectChange}
              optionLabelProp='label'
              style={{ width: '100%', marginBottom: 16 }}
              disabled={!!this.props.location.state}
          >
            {API_URLS.map(url => (
                <Option key={url.value} value={url.value} label={url.text + " - " + url.value}>{url.text + " - " + url.value}</Option>
            ))}
          </Select>
        </div>
        <TextArea
            label="JSON Blob"
            rows={10}
            placeholder="Paste JSON"
            value={textValue}
            onChange={this.onTextChange}
        />
        {warning && <span key="warning" style={{float: 'left', color: 'red'}}>Not a valid JSON</span>}
        {!warning && <span key="warning"
                           style={{float: 'left', color: 'red'}}></span>}
      </Modal>


    </Fragment>
  }
}

export default Jobs;
