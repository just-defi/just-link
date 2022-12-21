import React, {Component, Fragment} from 'react';
import {Table, Row, PageHeader, Button, Modal, Input, Tabs, Select} from 'antd';
import xhr from "axios/index";
import $ from 'jquery';
import {isJSON} from "../../utils/isJson";
import {getVrfFromDataSource} from "../../utils/getVrfFromDataSource";
import {getNonVrfFromDataSource} from "../../utils/getNonVrfFromDataSource";

const {TextArea} = Input;
const {TabPane} = Tabs;
const { Option } = Select;

const API_URL = process.env.API_URL;
const API_URLS = process.env.API_URLS;

const columns = [
  {
    title: 'ID',
    dataIndex: 'ID',
    key: 'ID',
  },
  {
    title: 'Initiator',
    dataIndex: 'Initiator',
    key: 'Initiator',
  },
  {
    title: 'Created',
    dataIndex: 'Created',
    key: 'Created',
  },
  {
    title: 'Node',
    dataIndex: 'Node',
    key: 'Node',
  },
  {
    title: 'DataSource',
    dataIndex: 'DataSource',
    key: 'DataSource',
  },
];

const merge = (target, source, key) => {
    return target.filter(targetEle => !source.some(sourceEle => targetEle[key] === sourceEle[key])).concat(source);
}


class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: false,
      vrfLoading: false,
      warning: false,
      visible: false,
      node: [],
      vrfNode: [],
      currentPage: 1,
      size: 10,
      total: 50,
      vrfTotal: 50,
      jobURL: API_URL,
    };
  }

  componentDidMount() {
    console.log("API_URLS: ", API_URLS);
    this.getJobs(1);
    this.getVrfs(1);
    if (this.props.location.state && this.props.location.state.create) {
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue: JSON.stringify(input, null, 4)});
    }
  }

  componentWillReceiveProps() {
  }

  getVrfs = (page) => {
    this.setState({vrfLoading: true});
    let vrfJobsInNodes = [];
    try {
      API_URLS.forEach((node)=> {
        console.log("Node: ", node);
        xhr.get(node.value + "/job/specs?page=" + page + "&size=10").then(
            (result) => {
              let data = result.data.data;
              vrfJobsInNodes = getVrfFromDataSource(data, node).concat(vrfJobsInNodes);
              this.setState({
                vrfNode: vrfJobsInNodes,
                vrfTotal: vrfJobsInNodes.length,
              });
            });
      });
      this.setState({
        vrfLoading: false,
      });
    } catch (e) {

    }
  }

  getJobs = (page) => {
    this.setState({loading: true});
    try {
      API_URLS.forEach((node) => {
        xhr.get(node.value + "/job/specs?page=" + page + "&size=10").then(
            (result) => {
              if (result.status === 200) {
                const results = merge(getNonVrfFromDataSource(result.data.data, node), this.state.node, "ID");
                this.setState({
                  node: results,
                  total: results.length,
                })
              }
            }
        )
      });
      this.setState({loading: false});
    } catch (e) {

    }
  }

  onSelectChange = (e) => {
    this.setState({jobURL: e.key});
  }


  onChange = (pageNumber) => {
    this.setState({currentPage: pageNumber.current})
    this.getJobs(pageNumber.current);
  }

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

    await xhr.post(this.state.jobURL + "/job/specs",
       JSON.parse(this.state.textValue)).then((result) => {

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
  };

  onTextChange = (e) => {
    this.setState({textValue: e.target.value})
  }

  success = (message) => {
    Modal.success({
      content: 'Successful!',
    });
  }

  error = (message) => {
    Modal.error({
      content: message,
    });
  }

  render() {
    let {
      visible,
      vrfLoading,
      loading,
      textValue,
      warning,
      node,
      vrfNode,
      currentPage,
      total,
      vrfTotal,
    } = this.state;
    return <Fragment>

      <PageHeader title="Jobs">

        <Button size='large' onClick={this.showModal} style={{float: 'right'}}>Create
          Job</Button>

      </PageHeader>
      <Row gutter={16}>
        <Tabs type="line">
          <TabPane tab="Price Service" key="1">

            <Table dataSource={node}
                   columns={columns}
                   loading={loading}
                   pagination={{
                     current: currentPage,
                     total: total,
                   }}
                   onChange={this.onChange}
                   onRow={record => {
                     return {
                       onClick: event => {
                         this.props.history.push("/jobs/" + record.ID);
                       },
                       onMouseEnter: (event) => {
                         $(event.target).css('cursor', 'pointer');
                       },
                     };
                   }}

            />


          </TabPane>
          <TabPane tab="VRF" key="2">

            <Table dataSource={vrfNode}
                   columns={columns}
                   loading={vrfLoading}
                   pagination={{
                     current: currentPage,
                     total: vrfTotal,
                   }}
                   onChange={this.onChange}
                   onRow={record => {
                     return {
                       onClick: event => {
                         this.props.history.push("/jobs/" + record.ID);
                       },
                       onMouseEnter: (event) => {
                         $(event.target).css('cursor', 'pointer');
                       },
                     };
                   }}
            />

          </TabPane>

        </Tabs>
      </Row>


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
                defaultValue={{ key: API_URL }}
                autoFocus={true}
                onSelect={this.onSelectChange}
                optionLabelProp='label'
                style={{ width: '100%', marginBottom: 16 }}
                labelInValue={true}
                disabled={!!textValue}
            >
                {API_URLS.map((url, idx) => (
                    <Option key={url.value} value={url.value} label={url.text + " - " + url.value}>{url.text + " - " + url.value}</Option>
                ))}
            </Select>
          <span>{JSON.stringify(Object.keys(this.state))}</span>
          <span>{JSON.stringify(this.props)}</span>
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
