<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

class AdminArrivalSharingController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->meta_title = $this->l('Recepção e Monitoramento de Traslados');
        $this->override_folder = '';
    }

    public function initContent()
    {
        parent::initContent();

        $this->context->smarty->assign(array(
            'module_name' => $this->module->displayName,
            'module_desc' => $this->module->description,
            'hotelLat'    => -8.052240,
            'hotelLng'    => -34.885650,
        ));

        $this->setTemplate('reception_dashboard.tpl');
    }

    public function createTemplate($tpl_name)
    {
        $templatePath = _PS_MODULE_DIR_ . $this->module->name . '/views/templates/admin/' . $tpl_name;
        if (file_exists($templatePath)) {
            return $this->context->smarty->createTemplate($templatePath, $this->context->smarty);
        }

        return parent::createTemplate($tpl_name);
    }
}
