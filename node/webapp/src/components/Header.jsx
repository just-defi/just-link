import React from 'react';
import {Row, Col, Icon, Menu, Button, Popover} from 'antd';
import {Link} from "react-router-dom";

import {enquireScreen} from 'enquire-js';

const LOGO_URL = 'logo-blue.svg';

class Header extends React.Component {
  state = {
    menuVisible: false,
    menuMode: 'horizontal',
    currentPath: '/'
  };

  componentDidMount() {
    enquireScreen((b) => {
      this.setState({menuMode: b ? 'inline' : 'horizontal'});
    });

    let path = window.location.hash;
    this.setState({currentPath: path.split('/')[1]==''?'jobs':path.split('/')[1]});
  }

  handleShowMenu = () => {
    this.setState({menuVisible: true})
  }
  handleCloseMenu = () => {
    this.setState({menuVisible: false})
  }
  handleMenuClick = e => {
    console.log('click ', e);
    this.setState({
      currentPath: e.key,
    });
    localStorage.setItem('PersistPath', e.key);
    sessionStorage.removeItem('jobUrl');
    sessionStorage.removeItem('nodeName');
  };

  onMenuVisibleChange = menuVisible => {
    this.setState({menuVisible});
  };


  render() {
    const {menuMode, menuVisible, currentPath} = this.state;

    const menu = (
        <Menu mode={menuMode} id="nav" key="nav" onClick={this.handleMenuClick} selectedKeys={[currentPath]}>
          <Menu.Item key="jobs">
            <Link to="/jobs">
              Jobs
            </Link>
          </Menu.Item>
          {/*
          <Menu.Item key="bridges">
            <Link to="/bridges">
              Bridges
            </Link>
          </Menu.Item>
          <Menu.Item key="transactions">
            <Link to="/transactions">
              Transactions
            </Link>
          </Menu.Item>
          <Menu.Item key="configuration">
            <a>Configuration</a>
          </Menu.Item>
          */}
        </Menu>
    );

    return (
        <div id="header" className="header">
          {menuMode === 'inline' ? (
              <Popover
                  overlayClassName="popover-menu"
                  placement="bottomRight"
                  content={<div onClick={this.handleCloseMenu}>{menu}</div>}
                  trigger="click"
                  visible={menuVisible}
                  arrowPointAtCenter
                  onVisibleChange={this.onMenuVisibleChange}
              >
                <Icon className="nav-phone-icon" type="menu"
                      onClick={this.handleShowMenu}
                />
              </Popover>
          ) : null}
          <Row>
            <Col xxl={4} xl={5} lg={8} md={8} sm={24} xs={24}>
              <div id="logo" to="/">
                <img src={LOGO_URL} alt="logo"/>
                <span>WinkLink Operation UI</span>
              </div>
            </Col>
            <Col xxl={20} xl={19} lg={16} md={16} sm={0} xs={0}>
              <div className="header-meta">
                {menuMode === 'horizontal' ? <div id="menu">{menu}</div> : null}
              </div>
            </Col>
          </Row>
        </div>
    );
  }
}

export default Header;
