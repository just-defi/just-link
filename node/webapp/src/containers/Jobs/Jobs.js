import React, {Component, Fragment} from 'react';
import {Button, Input, Modal, PageHeader, Row, Select, Table} from 'antd';
import xhr from "axios/index";
import $ from 'jquery';
import {isJSON} from "../../utils/isJson";
import {extractTaskHttpGet} from "../../utils/extractTaskHttpGet";

const {TextArea} = Input;
const {Option} = Select;

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
    filters: [{text: 'Price Service', value:'runlog'}, {text:'VRF', value:'randomnesslog'}],
    onFilter: (record, value)=> value.Initiator === record,
    key: 'Initiator',
    ellipsis: true,
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
      warning: false,
      visible: false,
      dataSource: [],
      currentPage: 1,
      size: 10,
      total: 50,
      jobURL: API_URL,
    };
  }

  componentDidMount() {
    this.getJobs(1);
    if (this.props.location.state && this.props.location.state.create) {
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue: JSON.stringify(input, null, 4)});
    }
  }

  componentWillReceiveProps() {}

  getJobs = (page) => {
    this.setState({loading:true});
    try {
      API_URLS.forEach((api) => {
        xhr.get(api.value + "/job/specs?page=" + page + "&size=10").then(
            (response) => {
              let data = response.data.data;
              let dataSource = [];
              data.forEach((item, index) => {
                let type = item.initiators[0].type;
                dataSource[index] = {
                  key: index,
                  ID: item.id,
                  Initiator: type,
                  Created: item.createdAt,
                  Node: api.text,
                  DataSource: type === "randomnesslog" ? "VRF" : extractTaskHttpGet(item),
                }
              })
              this.setState({
                loading: false,
                dataSource: dataSource,
                total: response.data.count
              });
            });
      });
    } catch(e) {

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

    await xhr.post(API_URL+"/job/specs", JSON.parse(this.state.textValue)).then((result) => {

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
      loading,
      textValue,
      warning,
      dataSource,
      currentPage,
      total
    } = this.state;
    return <Fragment>

      <PageHeader title="Jobs">

        <Button size='large' onClick={this.showModal} style={{float: 'right'}}>Create
          Job</Button>

      </PageHeader>


      <Row gutter={16}>

        <Table
            dataSource={dataSource}
            columns={columns}
            loading={loading}
            pagination={{current: currentPage, total: total}}
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
