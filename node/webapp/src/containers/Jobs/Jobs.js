import React, {Component, Fragment} from 'react';
import {Table, Row, PageHeader, Button, Modal, Input} from 'antd';
import xhr from "axios/index";
import $ from 'jquery';
import {isJSON} from "../../utils/isJson";

const {TextArea} = Input;

const API_URL = process.env.API_URL;

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
];


class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: false,
      warning: false,
      visible: false,
      dataSource: [],
      currentPage:1,
      size:10,
      total:50,
    };
  }


  componentDidMount() {
    this.getJobs(1);
    if(this.props.location.state && this.props.location.state.create){
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue:      JSON.stringify(input, null, 4)});
    }
  }

  componentWillReceiveProps() {}

  getJobs = (page) => {
    this.setState({loading:true});
    try {
      xhr.get(API_URL+"/job/specs?page=" + page + "&size=10").then((result) => {
        let data = result.data.data;
        let dataSource = [];
        data.forEach((item, index) => {
          dataSource[index] = {
            key: index,
            ID: item.id,
            Initiator: item.initiators[0].type,
            Created: item.createdAt,
          }
        })
        this.setState({loading: false, dataSource: dataSource, total: result.data.count});
      });
    } catch(e) {

    }
  }

  onChange = (pageNumber) => {
    this.setState({currentPage:pageNumber.current})
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
      if(result.data.msg=='success'){
        this.success()
      } else{
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
    let {visible, loading, textValue, warning, dataSource, currentPage, total} = this.state;
    return <Fragment>

      <PageHeader title="Jobs">

        <Button size='large' onClick={this.showModal} style={{float: 'right'}}>Create Job</Button>

      </PageHeader>


      <Row gutter={16}>

        <Table dataSource={dataSource}
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
        <TextArea
            label="JSON Blob"
            rows={10}
            placeholder="Paste JSON"
            value={textValue}
            onChange={this.onTextChange}
        />
        {warning && <span key="warning" style={{float: 'left', color: 'red'}}>Not a valid JSON</span>}
        {!warning && <span key="warning" style={{float: 'left', color: 'red'}}></span>}
      </Modal>


    </Fragment>
  }
}

export default Jobs;
