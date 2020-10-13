import React, {Component, Fragment} from 'react';
import {withRouter, Redirect} from "react-router-dom"
import {connect} from "react-redux"
import {Form, Icon, Input, Button, Checkbox, Card, Row, Col} from 'antd';
import {login} from "../actions/app"


class NormalLoginForm extends React.Component {
  handleSubmit = e => {
    e.preventDefault();
    this.props.form.validateFields((err, values) => {
      if (!err) {
        console.log('Received values of form: ', values);
        this.props.login(values.username, values.password);
        setTimeout(()=>{
          if (this.props.authentication) {
            console.log(localStorage.getItem('PersistPath'));
            if (localStorage.getItem('PersistPath')) {
              window.location.hash = ('/' + localStorage.getItem('PersistPath'));
            } else {
              this.props.history.push('/')
            }
          }
        },1000)
      }
    });
  };

  componentWillReceiveProps(nextProps) {
    // console.log(nextProps);
  }

  render() {
    const {getFieldDecorator} = this.props.form;
    return (
        <Row>
          <Col lg={8}></Col>
          <Col lg={8}>
            <div style={{
              minHeight: '400px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'end'
            }}>
              <div style={{flexGrow: '1'}}>
                <Card size='large'>
                  <Form onSubmit={this.handleSubmit} className="login-form">
                    <Form.Item>
                      {getFieldDecorator('username', {
                        rules: [{required: true, message: 'Please input your username!'}],
                      })(
                          <Input
                              prefix={<Icon type="user" style={{color: 'rgba(0,0,0,.25)'}}/>}
                              placeholder="Username"
                          />,
                      )}
                    </Form.Item>
                    <Form.Item>
                      {getFieldDecorator('password', {
                        rules: [{required: true, message: 'Please input your Password!'}],
                      })(
                          <Input
                              prefix={<Icon type="lock" style={{color: 'rgba(0,0,0,.25)'}}/>}
                              type="password"
                              placeholder="Password"
                          />,
                      )}
                    </Form.Item>
                    <Form.Item>

                      <Button type="primary" htmlType="submit" className="login-form-button">
                        Log in
                      </Button>

                    </Form.Item>
                  </Form>
                </Card>
              </div>
            </div>
          </Col>
          <Col lg={8}></Col>
        </Row>
    );
  }
}

const Login = Form.create({name: 'normal_login'})(NormalLoginForm);


function mapStateToProps(state) {
  return {
    account: state.app.account,
    authentication: state.app.authentication
  };
}

const mapDispatchToProps = {
  login,
};

export default connect(mapStateToProps, mapDispatchToProps, null, {pure: false})(withRouter((Login)))