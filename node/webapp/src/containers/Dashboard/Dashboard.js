import React, {Component, Fragment} from 'react';
import {Layout, Row, Col, Card, Button, Modal, Input} from 'antd';
import xhr from "axios/index";
import {isJSON} from "../../utils/isJson"

const {TextArea} = Input;
const {Header, Footer, Sider, Content} = Layout;

const API_URL = process.env.API_URL;

class Dashboard extends Component {

  constructor() {
    super();
    this.state = {
      loading: false,
      warning: false,
      visible: false,
    };
  }


  componentDidMount() {

  }

  showModal = () => {
    this.setState({
      visible: true,
    });
  };

  handleOk = async (e) => {
    if (!isJSON(this.state.textValue)) {
      this.setState({warning:true});
      return;
    }
    this.setState({warning:false, visible: false});
    let result = await xhr.post(API_URL+"/job/specs", JSON.parse(this.state.textValue));
    console.log(result);
  };

  handleCancel = e => {
    this.setState({
      visible: false,
    });
  };

  onTextChange = (e) => {
    this.setState({textValue: e.target.value})
  }


  render() {
    let {visible, loading, textValue, warning} = this.state;
    return <Fragment>


      <Row gutter={16}>
        <Col lg={16}>
          <Card title="Activity" extra={<Button size='large' onClick={this.showModal}>Create Job</Button>}>
            ……
          </Card>
        </Col>
        <Col lg={8}>
          <Card hoverable title="Balance">
            ……
          </Card>
          <Card hoverable title="Balance" style={{marginTop: '16px'}}>
            ……
          </Card>
          <Card hoverable title="Recently Created Jobs" style={{marginTop: '16px'}}>
            ……
          </Card>
        </Col>
      </Row>


      <Modal
          visible={visible}
          title="Title"
          onOk={this.handleOk}
          onCancel={this.handleCancel}
          footer={[
            <Button key="back" onClick={this.handleCancel}>
              Cancel
            </Button>,
            <Button key="submit" type="primary" loading={loading} onClick={this.handleOk}>
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
        {warning && <span key="warning" style={{float:'left', color:'red'}}>Not a valid JSON</span>}
        {!warning && <span key="warning" style={{float:'left', color:'red'}}></span>}
      </Modal>


    </Fragment>
  }
}

export default Dashboard;