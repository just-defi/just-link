import React, {Component, Fragment} from 'react';
import {Button, Input, Modal, PageHeader, Row, Select, Table, Tag, Icon, Tabs} from 'antd';
import xhr from "axios/index";
import $ from 'jquery';
import {isJSON} from "../../utils/isJson";
import {extractTaskHttpGet} from "../../utils/extractTaskHttpGet";

const {TextArea} = Input;
const {Option} = Select;
const {TabPane} = Tabs;

const API_URL = process.env.API_URL;
const API_URLS = process.env.API_URLS;
const DS_LIST = process.env.LIST_OF_DATASOURCE;
const DS_SIZE = process.env.DATASOURCE_SIZE_PER_RETRIEVAL;
const RANDOMNESS_LOG = 'randomnesslog';
const VRF = 'VRF';




class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: false,
      warning: false,
      visible: false,
      vrfDataSource: [],
      dataSource: [],
      searchText: '',
      searchedColumn:'',
      size: DS_SIZE,
      jobUrl: API_URL,
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
    onFilter: (value, record) => record[dataIndex].toString().includes(value),
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
              let data = response.data.data;
              data.forEach((item) => {
                let job = this.createJob(item, api.text);
                this.filterJobsAndSetToState(job);
              })
              this.setState({
                loading: false,
              });
            });
      });
    } catch (e) {

    }
  };

  createJob = (data, api) => {
    let type = data.initiators[0].type;
    let contractAddr = data.initiators[0].address;
    return {
      key: crypto.randomUUID(),
      Contract: contractAddr,
      ID: data.id,
      Initiator: type,
      Created: data.createdAt,
      Node: api,
      DataSource: type === RANDOMNESS_LOG ? VRF : extractTaskHttpGet(data, DS_LIST)
    };
  }
  filterJobsAndSetToState = (job) => {
    if (job.Initiator === RANDOMNESS_LOG) {
      this.setState({
        vrfDataSource: [...this.state.vrfDataSource, job],
      });
    } else {
      this.setState({
        dataSource: [...this.state.dataSource, job],
      });
    }
  }

  onSelectChange = (e) => {
    this.setState({jobUrl: e.key});
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

  generateTagColor(DataSource) {
    if (DataSource === VRF) {
      return 'red'
    } else if (DataSource === 'JUSTSWAP') {
      return 'green'
    } else {
      return 'blue'
    }
  }

  getDefaultValue = () => {
    if(this.props.location.state && this.props.location.state.jobUrl) {
      return {key:this.props.location.state.jobUrl};
    }
    return {key:API_URL};
}

  render() {
    let {
      visible,
      loading,
      textValue,
      warning,
      dataSource,
      size,
      vrfDataSource,
    } = this.state;

    const columns = [
      {
        title: 'Contract Address',
        dataIndex: 'Contract',
        key: 'Contract',
        ...this.getColumnSearchProps('Contract'),
      },
      {
        title: 'Job ID',
        dataIndex: 'ID',
        key: 'ID',
      },
      {
        title: 'Created',
        dataIndex: 'Created',
        key: 'Created',
        sorter: (a, b) => new Date(a.Created) - new Date(b.Created),
      },
      {
        title: 'Node',
        dataIndex: 'Node',
        key: 'Node',
      },
      {
        title: 'Data Source',
        dataIndex: 'DataSource',
        key: 'DataSource',
        filters: DS_LIST,
        onFilter: (record, value) => value.DataSource.toLowerCase().includes(record),
        render: DataSource => (
            <span>
              <Tag color={this.generateTagColor(DataSource)}>
                {DataSource}
              </Tag>
            </span>
        ),
      },
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
            dataSource={dataSource}
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
                  let selectedNode = API_URLS.find(url => url.text === record.Node).value
                  this.props.history.push({pathname: "/jobs/" +record.ID, state: {jobUrl: selectedNode}});
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
                  let selectedNode = API_URLS.find(url => url.text === record.Node).value
                  this.props.history.push({pathname: "/jobs/" +record.ID, state: {jobUrl: selectedNode}});;
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
              defaultValue={this.getDefaultValue()}
              autoFocus={true}
              onSelect={this.onSelectChange}
              optionLabelProp='label'
              style={{ width: '100%', marginBottom: 16 }}
              labelInValue={true}
              disabled={!!this.props.location.state}
          >
            {API_URLS.map((url, idx) => (
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
