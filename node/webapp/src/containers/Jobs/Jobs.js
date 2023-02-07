import React, {Component, Fragment} from 'react';
import {Button, Input, Modal, PageHeader, Row, Select, Table, Tag, Icon, Tabs, Tooltip} from 'antd';
import xhr from "axios/index";
import {isJSON} from "../../utils/isJson";
import moment from "moment";
import {Sorter as sorterUtil} from "../../utils/sorterUtil";

const {TextArea} = Input;
const {Option} = Select;
const {TabPane} = Tabs;
const {confirm} = Modal;

const API_URL = process.env.API_URL;
const API_URLS = JSON.parse(process.env.API_URLS);
const DS_SIZE = process.env.DATASOURCE_SIZE_PER_RETRIEVAL;
const LOCALE = process.env.LOCALE;
const TIMEZONE = process.env.TIMEZONE;
const RANDOMNESS_LOG = 'randomnesslog';
const PRICE_FEED = 'runlog';

class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: true,
      warning: false,
      visible: false,
      currentPage: 1,
      totalDataSource: 0,
      totalVRF: 0,
      vrfDataSource: [],
      dataSource: [],
      size: DS_SIZE,
      jobUrl: API_URL,
      providerList: [],
      page: 1,
    };
  };

  componentDidMount() {
    this.setState({loading: true});
    this.getJobs(this.state.currentPage, DS_SIZE);

    if (this.props.location.state && this.props.location.state.create && this.props.location.state.jobUrl) {
      this.setState({jobUrl: this.props.location.state.jobUrl});
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue: JSON.stringify(input, null, 4)});
    }
    window.sessionStorage.removeItem('jobUrl');
    window.sessionStorage.removeItem('nodeName');
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
  }

  handleReset = clearFilters => {
    clearFilters();
  };

  getJobs = (page, size) => {
    this.setState({loading: true});
    let promises = [];
    API_URLS.map(api => {
      const url = (type) => `${api.value}/job/specs/active?type=${type}&page=${page}&size=${size}`;
      promises.push(xhr.get(url(PRICE_FEED), {timeout: 1000 * 10})
          .then(response => response.data.data.map(job => this.filterJobsAndSetToState(this.createJob(job, api))))
          .catch(e => console.log(e)));
      promises.push(xhr.get(url(RANDOMNESS_LOG), {timeout: 1000 * 10})
          .then(response => response.data.data.map(job => this.filterJobsAndSetToState(this.createJob(job, api))))
          .catch(e => console.log(e)));
    });
    Promise.all(promises).then(() => this.setState({loading: false}));
  }

  createJob = (data, api) => {
    const params = JSON.parse(data.params);
    return {
      key: data.address + data.jobSpecsId + api.text,
      Contract: data.address,
      ID: data.jobSpecsId,
      Initiator: data.type,
      Created: {
        date: new Date(data.createdAt).toLocaleString(LOCALE, {timeZone: TIMEZONE}),
        epoch: moment(data.createdAt).unix(),
      },
      Updated: {
        date: new Date(data.updatedAt).toLocaleString(LOCALE, {timeZone: TIMEZONE}),
        epoch: moment(data.updatedAt).unix(),
      },
      Node: api.text,
      DataSource: {
        task: JSON.parse(data.params).tasks.map(task => task.type).join(" / "),
        ...(this.generateTagColor(params.tasks[0]))
      },
      LastRunResult: `${api.value}/job/result/${data.jobSpecsId}` ,
      Code: JSON.stringify(params, null, 2),
      PublicKey: (params.initiators[0].type === RANDOMNESS_LOG) ?
          params.tasks[0].params.publicKey : null,
    };
  }

  filterJobsAndSetToState = (job) => {
    if (job.Initiator === RANDOMNESS_LOG) {
      if (this.state.vrfDataSource.findIndex(o => o.key === job.key) < 0) {
        this.setState({
          vrfDataSource: [...this.state.vrfDataSource, job],
        });
      }
    } else {
      if (this.state.dataSource.findIndex(o =>  o.key === job.key) < 0) {
        this.setState({
          dataSource: [...this.state.dataSource, job],
        });
      }
    }
  }

  onSelectChange = (e) => {
    this.setState({jobUrl: e});
  };

  showModal = () => {
    if (!this.state.jobUrl) { this.setState({jobUrl: API_URL}); }
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
    await xhr.post(`${this.state.jobUrl}/job/specs`, JSON.parse(this.state.textValue)).then((result) => {

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
      jobUrl: '',
      edit: '',
      create: '',
      warning: false,
    });
    delete this.props.location.state;
  };

  onTextChange = (e) => {
    this.setState({textValue: e.target.value})
  };

  success = () => {
    Modal.success({
      content: 'Successful!',
      onOk: () => window.location.reload(true),
    });
  };

  error = (message) => {
    Modal.error({
      content: message,
    });
  };

  generateTagColor = (task) => {
    let url, tag, color ;

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

  showDeleteConfirm = (record) => {
    confirm({
      title: 'Confirm job spec deletion?',
      content: 'Job spec will be archived',
      okText: 'Confirm',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk() {
        const selectedNode = API_URLS.find(url => url.text === record.Node).value;
        const url = `${selectedNode}/job/specs/${record.ID}`;
        xhr.delete(url).then((result) => {
          if (result.error) {
            Modal.error({content: result.error})
          }
          if(result.data && result.data.msg && result.data.msg === 'success'){
            Modal.success({content: result.data.msg, onOk: () => window.location.reload(true)});
          } else{
            Modal.error({content: result.data.msg});
          }
        }).catch((e) => {
          Modal.error({content: e})
        });
      },
      onCancel() {
      },
    });
  }

  render() {
    let {
      visible,
      loading,
      textValue,
      warning,
      dataSource,
      vrfDataSource,
      providerList,
    } = this.state;

    const commonColumns = [
      {
        title: 'Contract Address',
        dataIndex: 'Contract',
        key: 'Contract',
        ellipsis: true,
        ...this.getColumnSearchProps('Contract'),
        sorter: (a,b, sortOrder) => sorterUtil.STRING(a.Contract, b.Contract, sortOrder),
      },
      {
        title: 'Node',
        dataIndex: 'Node',
        key: 'Node',
        ellipsis: true,
        filters: API_URLS,
        onFilter: (record, {Node}) => record.toLowerCase().includes(API_URLS.find((url)=> url.text === Node).value),
        sorter: (a,b, sortOrder) => sorterUtil.STRING(a.Node, b.Node, sortOrder),
      },
      {
        title: 'Job ID',
        dataIndex: 'ID',
        ellipsis: true,
        ...this.getColumnSearchProps('ID'),
        key: 'ID',
        sorter: (a, b) => sorterUtil.DEFAULT(a.ID, b.ID),
      },
      {
        title: 'Last Updated time',
        dataIndex: 'Updated.date',
        key: 'Updated',
        ellipsis: true,
        sorter: (a, b, sortOrder) => sorterUtil.DATE(a.Updated.epoch, b.Updated.epoch, sortOrder),
        sortDirection: ['descend', 'ascend'],
      },
      {
        title: 'Last Run Time',
        dataIndex: 'Created.date',
        key: 'Created',
        ellipsis: true,
        sorter: (a, b, sortOrder) => sorterUtil.DATE(a.Created.epoch, b.Created.epoch, sortOrder),
        defaultSortOrder: 'descend',
        sortDirection: ['descend', 'ascend'],
      },
      {
        title: 'Current Result',
        dataIndex: 'LastRunResult',
        key: 'LastRunResult',
        width: 90,
        render: lastRunResult => {
          if (lastRunResult) {
            return <a href={lastRunResult} target="_blank" rel="noopener noreferrer">Result</a>;
          }
        }
      },
    ]

    const commonActions = [
      {
        title: 'Actions',
        width: 110,
        render: (record) => {
          return <div>
            <Button.Group size={'small'}>
              <Tooltip title={'view'}>
                <Button icon='select'
                        className={'wink-btn-success'}
                        onClick={event => {
                          let selectedNode = API_URLS.find(url => url.text === record.Node).value;
                          window.sessionStorage.setItem('jobUrl', selectedNode);
                          window.sessionStorage.setItem('nodeName', record.Node);
                          this.props.history.push({pathname: "/jobs/" + record.ID, state: {jobUrl: selectedNode, nodeName: record.Node}});
                        }} />
              </Tooltip>
              <Tooltip title={'edit'}>
                <Button icon='edit'
                        className={'wink-btn-primary'}
                        onClick={event => {
                          const selectedNode = API_URLS.find(url => url.text === record.Node).value;
                          this.setState({code:JSON.stringify(JSON.parse(record.Code)), create:true, edit:true, jobUrl: selectedNode }, () => {
                            this.showModal();
                            let input = eval('(' + this.state.code + ')');
                            this.setState({textValue: JSON.stringify(input, null, 4),  jobUrl: selectedNode});
                          });
                        }}
                />
              </Tooltip>
            </Button.Group>
            &nbsp;
            <Button.Group size={'small'}>
              <Tooltip title={'delete'}>
                <Button type='danger'
                        icon='delete'
                        onClick={ event => this.showDeleteConfirm(record)}
                />
              </Tooltip>
            </Button.Group>
          </div>
        }
      }
    ];

    const columns = [
        ...commonColumns,
      {
        title: 'Data Source',
        dataIndex: 'DataSource',
        key: 'DataSource',
        width: 130,
        filters: providerList,
        onFilter: (value, record) => value.toUpperCase().includes(record.DataSource.tag.toUpperCase()),
        render: dataSource => (
            <span>
                <Tooltip title={dataSource.task} overlayStyle={{'whiteSpace': 'nowrap', maxWidth: '500px'}} >
                  <Tag color={dataSource.color}>
                    {dataSource.tag}
                  </Tag>
                </Tooltip>
             </span>
        ),
      },
        ...commonActions
    ];

    const vrfColumns = [
        ...commonColumns,
      {
        title: 'Public Key',
        dataIndex: 'PublicKey',
        key: 'PublicKey',
        width: 200,
        ...this.getColumnSearchProps('PublicKey'),
      },
        ...commonActions
    ];

    const pageSizeOption = ['10','25','50','100'];

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
            // scroll={{ y: "calc(100vh - 400px)" }}
        />


      </Row>
      </TabPane>
      <TabPane tab="VRF" key="2">

        <Table
            dataSource={vrfDataSource}
            columns={vrfColumns}
            loading={loading}
            pagination={{
              pageSizeOptions: pageSizeOption,
              showSizeChanger: true,
              showQuickJumper: true,
              hideOnSinglePage: false,
            }}
            // scroll={{ y: "calc(100vh - 400px)" }}
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
              defaultValue={this.state.jobUrl || API_URL}
              value={this.state.jobUrl || API_URL}
              autoFocus={true}
              onSelect={this.onSelectChange}
              optionLabelProp='label'
              style={{ width: '100%', marginBottom: 16 }}
              disabled={!!this.props.location.state || !!this.state.edit}
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
